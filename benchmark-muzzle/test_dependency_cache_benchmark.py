import json
import os
from pathlib import Path
import subprocess
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

import benchmark
import ci_benchmark as ci
import dependency_cache_benchmark as dependency
import test_ci_benchmark as fixtures


class DependencyCacheTest(unittest.TestCase):
    def cache(self, root, name, files):
        home = root / name
        home.mkdir()
        for path, value in files.items():
            target = home / path
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(value, encoding="utf-8")
        archive = root / f"{name}.tar.gz"
        ci.snapshot(home, archive)
        return archive

    def test_composition_changes_only_dependency_cache_payload(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            original = self.cache(root, "original", {
                "caches/build-cache-1/value": "original task output",
                "caches/modules-2/old.jar": "old",
                "gradle.properties": "original properties",
            })
            warmed = self.cache(root, "warmed", {
                "caches/build-cache-1/value": "changed task output",
                "caches/modules-2/old.jar": "old", "caches/modules-2/new.jar": "new",
                "gradle.properties": "changed properties",
            })
            result = dependency.compose_snapshot(original, warmed, root / "combined.tar.gz")
            ci.restore_snapshot(root / "combined.tar.gz", root / "result")
            self.assertEqual((root / "result/caches/build-cache-1/value").read_text(), "original task output")
            self.assertEqual((root / "result/gradle.properties").read_text(), "original properties")
            self.assertEqual((root / "result/caches/modules-2/new.jar").read_text(), "new")
            self.assertEqual(result["dependency_files"], 2)
            self.assertEqual(result["dependency_bytes"], 6)
            self.assertEqual(result["sha256"], benchmark.digest(root / "combined.tar.gz"))
            with self.assertRaisesRegex(ValueError, "new file"):
                dependency.compose_snapshot(original, warmed, original)
        for name in ("/outside", "caches/modules-2/../build-cache-1/value"):
            with self.subTest(name=name), self.assertRaisesRegex(ValueError, "Unsafe"):
                dependency.dependency_entry(name)
        self.assertFalse(dependency.dependency_entry("caches/modules-20/value"))

    def test_candidate_archive_is_used_only_by_candidate(self):
        fixture = fixtures.CiBenchmarkTest()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            runner = fixture.runner(root)
            runner.candidate_snapshot = self.cache(root, "candidate-cache", {
                "caches/value": "original", "caches/modules-2/warmed": "present",
            })
            digest = benchmark.digest(runner.candidate_snapshot)
            observed = []

            def execute(command, **kwargs):
                if kwargs.get("log") is not None:
                    home = Path(kwargs["env"]["GRADLE_USER_HOME"])
                    observed.append((kwargs["log"].parent.name, (home / "caches/modules-2/warmed").exists()))
                return fixture.fake_build(command, **kwargs)

            with patch.object(benchmark, "run", side_effect=execute):
                runner.measure()
            self.assertEqual(observed, [("baseline", False), ("control", False), ("candidate", True)])
            self.assertEqual(benchmark.digest(runner.candidate_snapshot), digest)

    def test_warmups_accumulate_other_seeds_but_discard_compilation_cache_changes(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            original = self.cache(root, "original", {
                "caches/value": "original", "caches/modules-2/original.jar": "original",
            })
            source = root / "source"
            source.mkdir()
            args = SimpleNamespace(
                repository=source, work_dir=root / "warming", revision="source",
                partition=2, warm_seeds=2, cache_snapshot=original,
                snapshot_sha256=benchmark.digest(original), output=root / "candidate.tar.gz",
            )
            comparison = ci.Comparison

            def factory(arguments):
                runner = object.__new__(comparison)
                runner.args = arguments
                runner.source = source
                runner.root = arguments.work_dir
                runner.root.mkdir()
                runner.reports = runner.root / "reports"
                runner.reports.mkdir()
                runner.snapshot = arguments.cache_snapshot
                runner.candidate_snapshot = None
                runner.revisions = {"baseline": "source", "candidate": "source"}
                runner.identity = {"workload": {"seed": arguments.seed, "versions_limit": 10}}
                runner.env = dict(os.environ)
                runner.samples = []
                return runner

            def execute(command, *, cwd=None, env=None, log=None):
                if "rev-parse" in command:
                    return b"source"
                if command[:2] == ["git", "clone"]:
                    Path(command[-1]).mkdir()
                elif log is not None:
                    home = Path(env["GRADLE_USER_HOME"])
                    seed = int(next(value.split("=")[1] for value in command if value.startswith("-PmuzzleBenchmarkSeed=")))
                    if seed == 1731:
                        self.assertTrue((home / "caches/modules-2/seed-1730.jar").exists())
                    (home / f"caches/modules-2/seed-{seed}.jar").write_text(str(seed))
                    (home / "caches/value").write_text("new compilation cache")
                    fixtures.write_record(log.parent / "assertions")
                    log.write_text(fixtures.build_log(), encoding="utf-8")
                    profile = Path(cwd) / "build/reports/profile"
                    profile.mkdir(parents=True)
                    (profile / "profile-fixture.html").write_text(fixtures.PROFILE, encoding="utf-8")
                    metadata = Path(cwd) / "example/build/muzzleRepo"
                    metadata.mkdir(parents=True)
                    (metadata / "maven-metadata-fixture.xml").write_text("<metadata/>")
                return b""

            with patch.object(ci, "Comparison", side_effect=factory), patch.object(benchmark, "run", side_effect=execute):
                dependency.warm(args)
            plan = json.loads((args.work_dir / "reports/warming.json").read_text())
            self.assertTrue(plan["complete"])
            self.assertEqual(plan["warm_seeds"], [1730, 1731])
            self.assertNotIn(plan["measured_seed"], plan["warm_seeds"])
            ci.restore_snapshot(args.output, root / "result")
            self.assertEqual((root / "result/caches/value").read_text(), "original")
            self.assertTrue((root / "result/caches/modules-2/seed-1730.jar").exists())
            self.assertTrue((root / "result/caches/modules-2/seed-1731.jar").exists())
            args.work_dir = root / "failed-warming"
            args.output = root / "failed-candidate.tar.gz"

            def failed(command, **kwargs):
                if kwargs.get("log") is not None:
                    kwargs["log"].write_text("BUILD FAILED")
                    raise subprocess.CalledProcessError(1, command)
                return execute(command, **kwargs)

            with patch.object(ci, "Comparison", side_effect=factory), patch.object(benchmark, "run", side_effect=failed):
                with self.assertRaises(subprocess.CalledProcessError):
                    dependency.warm(args)
            self.assertTrue((args.work_dir / "reports/seed-1730/candidate/console.log").exists())
            self.assertTrue((args.work_dir / "reports/failure.txt").exists())
            self.assertFalse(json.loads((args.work_dir / "reports/warming.json").read_text())["complete"])

    def test_report_rejects_bad_warming_before_writing_a_success_summary(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            inputs = root / "inputs"
            inputs.mkdir()
            fixtures.CiBenchmarkTest().report_fixture(inputs, 1, 1, dict.fromkeys(ci.LABELS, 100))
            plan = root / "plans/dependency-cache-plan-1"
            plan.mkdir(parents=True)
            ci.write_json(plan / "warming.json", {"complete": False})
            args = SimpleNamespace(inputs=inputs, plans=root / "plans", output=root / "summary", rounds=1, partitions=[1])
            with self.assertRaisesRegex(ValueError, "warming plan"):
                dependency.report(args)
            self.assertFalse(args.output.exists())

    def test_cross_round_warmed_cache_must_match_but_slices_can_have_distinct_caches(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for partition in (1, 2):
                for round_number in (1, 2):
                    folder = fixtures.CiBenchmarkTest().report_fixture(root, round_number, partition, dict.fromkeys(ci.LABELS, 100))
                    path = folder / "identity.json"
                    identity = json.loads(path.read_text())
                    identity.update(experiment="dependency-cache", candidate_cache_snapshot_sha256=f"cache-{partition}")
                    identity["revisions"] = dict.fromkeys(("baseline", "candidate"), "before")
                    ci.write_json(path, identity)
                    result = json.loads((folder / "samples.json").read_text())
                    for sample in result["samples"]:
                        sample["revision"] = "before"
                    ci.write_json(folder / "samples.json", result)
            shared, _, _ = ci.load_reports(root, 2, [1, 2])
            self.assertEqual(shared["candidate_cache_snapshots"], {1: "cache-1", 2: "cache-2"})
            path = root / "muzzle-ci-2-1/identity.json"
            identity = json.loads(path.read_text())
            identity["candidate_cache_snapshot_sha256"] = "different"
            ci.write_json(path, identity)
            with self.assertRaisesRegex(ValueError, "Warmed cache across rounds"):
                ci.load_reports(root, 2, [1, 2])

    def test_report_verifies_plan_and_labels_the_same_revision_cache_experiment(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            inputs = root / "inputs"
            inputs.mkdir()
            folder = fixtures.CiBenchmarkTest().report_fixture(inputs, 1, 1, {
                "baseline": 100, "control": 110, "candidate": 50,
            })
            identity = json.loads((folder / "identity.json").read_text())
            identity.update(
                revisions={"baseline": "before", "candidate": "before"},
                experiment="dependency-cache", candidate_cache_snapshot_sha256="warmed",
                workload={"seed": 1729},
            )
            ci.write_json(folder / "identity.json", identity)
            samples = json.loads((folder / "samples.json").read_text())
            for sample in samples["samples"]:
                sample["revision"] = "before"
            ci.write_json(folder / "samples.json", samples)
            plan = root / "plans/dependency-cache-plan-1"
            plan.mkdir(parents=True)
            ci.write_json(plan / "warming.json", {
                "complete": True, "partition": 1, "revision": "before", "warm_seeds": [1730, 1731],
                "initial_snapshot_sha256": "same-snapshot", "initial_compressed_bytes": 1000,
                "candidate_snapshot": {"sha256": "warmed", "compressed_bytes": 1500},
                "warm_assertion_tasks": [],
            })
            args = SimpleNamespace(inputs=inputs, plans=root / "plans", output=root / "summary", rounds=1, partitions=[1])
            dependency.report(args)
            result = json.loads((args.output / "results.json").read_text())
            self.assertEqual(result["maximum_slice_paired"]["candidate"]["delta_ms"]["median"], -50)
            self.assertIn("cache-cleanup: never", (args.output / "summary.md").read_text())
            self.assertIn("condition-specific immutable snapshots", (args.output / "summary.md").read_text())
            self.assertEqual(result["cache_warming"]["1"]["warm_seeds"], [1730, 1731])


if __name__ == "__main__":
    unittest.main()
