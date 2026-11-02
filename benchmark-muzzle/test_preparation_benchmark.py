import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

import benchmark
import ci_benchmark as ci
import preparation_benchmark as preparation


class PreparationBenchmarkTest(unittest.TestCase):
    def test_refuses_cleanup_outside_hosted_ci_before_creating_output(self):
        with tempfile.TemporaryDirectory() as directory:
            args = SimpleNamespace(round=1, output=Path(directory) / "output")
            with patch.dict(os.environ, GITHUB_ACTIONS="", RUNNER_ENVIRONMENT=""), patch.object(
                benchmark, "run",
            ) as execute:
                with self.assertRaisesRegex(ValueError, "GitHub-hosted Linux"):
                    preparation.measure(args)
            execute.assert_not_called()
            self.assertFalse(args.output.exists())

    def test_guard_preserves_headroom_boundary_and_cleanup_failures(self):
        bash = shutil.which("bash")
        if os.name == "nt" and bash and Path(bash).parent.name.lower() != "bin":
            bash = None
        if os.name == "nt" and not bash:
            git = shutil.which("git")
            bash = Path(git).resolve().parents[1] / "bin/bash.exe" if git else None
        if not bash or not Path(bash).is_file():
            self.skipTest("Bash is unavailable")
        driver = """
        python3() { if [[ "${QUERY_FAIL:-0}" == 1 ]]; then return 7; fi; echo "$FREE_BYTES"; }
        bash() { echo "CLEANUP:$*"; return "${CLEANUP_EXIT:-0}"; }
        source .github/scripts/gha-muzzle-free-disk-space.sh "$@"
        """
        for available in (preparation.MINIMUM_BYTES - 1, preparation.MINIMUM_BYTES, preparation.MINIMUM_BYTES + 1):
            output = benchmark.run(
                [str(bash), "-c", driver], cwd=preparation.ROOT,
                env=dict(os.environ, FREE_BYTES=str(available)),
            ).decode()
            self.assertEqual("decision=skip" in output, available >= preparation.MINIMUM_BYTES)
            self.assertEqual("CLEANUP:" in output, available < preparation.MINIMUM_BYTES)
            self.assertIn(f"threshold_bytes={preparation.MINIMUM_BYTES}", output)
            if available < preparation.MINIMUM_BYTES:
                self.assertIn("CLEANUP:-e .github/scripts/gha-free-disk-space.sh", output)
        for env in (
            dict(FREE_BYTES="0", CLEANUP_EXIT="3"),
            dict(FREE_BYTES="0", QUERY_FAIL="1"),
        ):
            with self.assertRaises(subprocess.CalledProcessError):
                benchmark.run([str(bash), "-c", driver], cwd=preparation.ROOT, env=dict(os.environ, **env))
        with self.assertRaises(subprocess.CalledProcessError):
            benchmark.run([str(bash), "-c", driver, "driver", "unexpected"], cwd=preparation.ROOT)

    def test_pairs_alternate_order_and_report_only_measured_preparation(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            observed = []

            def execute(command, *, cwd=None, log=None):
                if log is None:
                    return b"source"
                observed.append(log.stem)
                log.write_text("MUZZLE_DISK_PREPARATION decision=skip " if log.stem == "candidate" else "cleanup")
                return b""

            for number in (1, 2):
                args = SimpleNamespace(round=number, output=root / f"muzzle-preparation-{number}")
                with patch.object(benchmark, "run", side_effect=execute), patch.object(
                    preparation.shutil, "disk_usage", return_value=SimpleNamespace(free=preparation.MINIMUM_BYTES),
                ), patch.object(
                    preparation.sys, "platform", "linux",
                ), patch.dict(
                    os.environ, GITHUB_ACTIONS="true", RUNNER_ENVIRONMENT="github-hosted",
                ):
                    preparation.measure(args)
            self.assertEqual(observed, ["candidate", "baseline", "baseline", "candidate"])
            args = SimpleNamespace(inputs=root, output=root / "summary", rounds=2)
            preparation.report(args)
            result = json.loads((args.output / "results.json").read_text())
            self.assertTrue(result["complete"])
            self.assertIn("Disk-preparation step only", result["scope"])
            bad = root / "muzzle-preparation-1/preparation.json"
            value = json.loads(bad.read_text())
            value["complete"] = False
            ci.write_json(bad, value)
            args.output = root / "invalid-summary"
            with self.assertRaisesRegex(ValueError, "Incomplete"):
                preparation.report(args)
            self.assertFalse(args.output.exists())


if __name__ == "__main__":
    unittest.main()
