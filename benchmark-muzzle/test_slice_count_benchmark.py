import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

import benchmark
import ci_benchmark as ci
import slice_count_benchmark as slices
import test_ci_benchmark as fixtures


class SliceCountBenchmarkTest(unittest.TestCase):
    def fixture(self, root, number, partition, durations):
        directory = root / f"muzzle-slices-{number}-{partition}"
        directory.mkdir()
        ci.write_json(directory / "identity.json", {
            "revision": "source", "partition": partition, "round": number,
            "cache_snapshot_sha256": "snapshot", "experiment": "slice-count",
            "plan": slices.plan(number, partition),
        })
        tasks = [f":instrumentation:{name}{partition}:muzzle-AssertPass-fixture-library-1.0" for name in ("a", "b")]
        for command in slices.plan(number, partition):
            report = directory / command["role"]
            report.mkdir()
            label = command["label"]
            output = report / label
            output.mkdir()
            selected = tasks if command["slices"] == 4 else [tasks[command["role"] == "candidate-b"]]
            ci.write_json(report / "identity.json", {
                "revisions": dict.fromkeys(("baseline", "candidate"), "source"),
                "partition": command["partition"], "round": number,
                "muzzle_slices": command["slices"], "cache_snapshot_sha256": "snapshot",
                "workload": {"seed": 1729, "versions_limit": 10},
            })
            log = fixtures.build_log(selected[0]).replace(
                f"> Task {selected[0]}\n",
                "> Task :commonCompile FROM-CACHE\n"
                + f"> Task :instrumentation:muzzle{command['partition']}\n"
                + "".join(f"> Task {task}\n" for task in selected),
            )
            (output / "console.log").write_text(log)
            for task in selected:
                fixtures.write_record(output / "assertions", fixtures.RECORD.replace(fixtures.ASSERTION, task))
            profile = output / "profile/profile-fixture.html"
            profile.parent.mkdir()
            profile.write_text(fixtures.PROFILE.replace(
                f"<tr><td>{fixtures.ASSERTION}</td><td>0.5s</td><td></td></tr>",
                "".join(f"<tr><td>{task}</td><td>0.5s</td><td></td></tr>" for task in selected),
            ))
            projects = {}
            for task in selected:
                project = task.split(":muzzle-Assert")[0]
                metadata = output / "metadata" / project.removeprefix(":").replace(":", "/") / "build/muzzleRepo/maven-metadata-fixture.xml"
                metadata.parent.mkdir(parents=True)
                metadata.write_text("<metadata/>")
                projects[project] = ["maven-metadata-fixture.xml"]
            sample = {
                "label": label, "revision": "source", "process_success": True,
                "command_ms": durations[command["role"]], "assertions": len(selected),
                "task_outcomes": benchmark.task_outcomes(log),
                "profile": ci.profile_metrics(profile),
                "discovery": {
                    "projects": projects, "project_count": len(projects),
                    "metadata_documents": len(projects),
                    "metadata_sha256": benchmark.inventory(output / "metadata"),
                },
            }
            ci.write_json(report / "samples.json", {"complete": True, "samples": [sample]})
            ci.write_json(output / "command.json", [
                f":instrumentation:muzzle{command['partition']}", f"-PmuzzleSlices={command['slices']}",
            ])
        ci.write_json(directory / "complete.json", {"complete": True})
        return directory

    def test_primary_estimator_compares_four_vs_eight_command_maxima(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for number, partition, baseline, first, second in (
                (1, 1, 100, 40, 60), (1, 2, 90, 150, 30),
                (2, 1, 10, 5, 7), (2, 2, 100, 40, 50),
            ):
                self.fixture(root, number, partition, {
                    "baseline": baseline, "control": baseline + 1,
                    "candidate-a": first, "candidate-b": second,
                })
            args = SimpleNamespace(inputs=root, output=root / "summary", rounds=2, partitions=[1, 2])
            slices.aggregate(args)
            result = json.loads((args.output / "results.json").read_text())
            self.assertEqual(result["maximum_slice_paired"]["candidate"]["delta_ms"]["median"], 0)
            self.assertEqual(result["maximum_slice_paired"]["candidate"]["delta_ms"]["min"], -50)
            self.assertEqual(result["maximum_slice_paired"]["candidate"]["delta_ms"]["max"], 50)
            self.assertEqual(
                [command["role"] for command in slices.plan(2, 3)],
                ["candidate-b", "candidate-a", "control", "baseline"],
            )
            self.assertEqual([command["partition"] for command in slices.plan(1, 3)], [3, 3, 3, 7])

    def test_missing_coverage_changed_caches_or_changed_commands_invalidate_results(self):
        for defect in ("workload", "cache", "command", "identity"):
            with self.subTest(defect=defect), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                folder = self.fixture(root, 1, 1, dict.fromkeys(slices.ROLES, 100))
                if defect == "workload":
                    path = next((folder / "candidate-b/candidate/assertions").glob("*.txt"))
                    path.write_text(path.read_text().replace("sha256", "different"))
                elif defect == "cache":
                    path = folder / "candidate-b/samples.json"
                    value = json.loads(path.read_text())
                    value["samples"][0]["task_outcomes"][":commonCompile"] = "EXECUTED"
                    ci.write_json(path, value)
                    log = folder / "candidate-b/candidate/console.log"
                    log.write_text(log.read_text().replace(":commonCompile FROM-CACHE", ":commonCompile"))
                elif defect == "command":
                    ci.write_json(folder / "candidate-b/candidate/command.json", [":instrumentation:muzzle1", "-PmuzzleSlices=4"])
                else:
                    path = folder / "candidate-b/identity.json"
                    value = json.loads(path.read_text())
                    value["cache_snapshot_sha256"] = "different"
                    ci.write_json(path, value)
                with self.assertRaises(ValueError):
                    slices.validate_group(folder)

    def test_failed_command_preserves_logs_without_retry(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "source"
            source.mkdir()
            args = SimpleNamespace(
                repository=source, work_dir=root / "run", revision="source", round=1,
                partition=2, cache_snapshot=root / "snapshot", snapshot_sha256="hash",
            )
            reports = args.work_dir / "baseline/reports"

            def execute(label):
                reports.mkdir(parents=True)
                (reports / "console.log").write_text("FAILED")
                raise ValueError("fixture failure")

            runner = SimpleNamespace(reports=reports, execute=execute)
            with patch.object(benchmark, "run", side_effect=[b"source", b'gradleProperty("muzzleSlices")']), patch.object(
                ci, "Comparison", return_value=runner,
            ) as factory:
                with self.assertRaisesRegex(ValueError, "fixture failure"):
                    slices.measure(args)
            self.assertEqual(factory.call_count, 1)
            self.assertEqual((args.work_dir / "reports/baseline/console.log").read_text(), "FAILED")
            self.assertTrue((args.work_dir / "reports/failure.txt").exists())
            self.assertFalse((args.work_dir / "reports/complete.json").exists())


if __name__ == "__main__":
    unittest.main()
