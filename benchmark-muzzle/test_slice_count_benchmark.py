import json
from pathlib import Path
import re
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

import benchmark
import ci_benchmark as ci
import slice_count_benchmark as slices
import test_ci_benchmark as fixtures


class SliceCountBenchmarkTest(unittest.TestCase):
    def test_ci_routes_all_eight_slices_with_the_corresponding_layout(self):
        workflow = (Path(__file__).resolve().parents[1] / ".github/workflows/reusable-muzzle.yml").read_text(encoding="utf-8")
        self.assertEqual(
            re.findall(r'^\s+- ":instrumentation:muzzle(\d+)"$', workflow, re.MULTILINE),
            [str(number) for number in range(1, 9)],
        )
        self.assertIn("run: ./gradlew ${{ matrix.task }} -PmuzzleSlices=8", workflow)

    def fixture(self, root, number, partition, durations, *, eager=False):
        directory = root / f"muzzle-slices-{number}-{partition}"
        directory.mkdir()
        ci.write_json(directory / "identity.json", {
            "revision": "source", "partition": partition, "round": number,
            "cache_snapshot_sha256": "snapshot", "experiment": "slice-count",
            "plan": slices.plan(number, partition),
            "discovery_mode": "eager" if eager else "slice-aware",
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
                **({"discovery_mode": "eager"} if eager else {}),
            })
            log = fixtures.build_log(selected[0]).replace(
                f"> Task {selected[0]}\n",
                "> Task :commonCompile FROM-CACHE\n"
                + f"> Task :instrumentation:muzzle{command['partition']}\n"
                + "".join(f"> Task {task}\n" for task in selected),
            )
            (output / "console.log").write_text(log)
            if eager:
                (output / "console.log").write_text(log + "MUZZLE_BENCHMARK_DISCOVER_ALL true\n")
            for task in selected:
                fixtures.write_record(output / "assertions", fixtures.RECORD.replace(fixtures.ASSERTION, task))
            profile = output / "profile/profile-fixture.html"
            profile.parent.mkdir()
            profile.write_text(fixtures.PROFILE.replace(
                f"<tr><td>{fixtures.ASSERTION}</td><td>0.5s</td><td></td></tr>",
                "".join(f"<tr><td>{task}</td><td>0.5s</td><td></td></tr>" for task in selected),
            ))
            projects = {}
            for task in (tasks if eager else selected):
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
                **({"discover_all": True} if eager else {}),
            }
            ci.write_json(report / "samples.json", {"complete": True, "samples": [sample]})
            ci.write_json(output / "command.json", [
                f":instrumentation:muzzle{command['partition']}", f"-PmuzzleSlices={command['slices']}",
            ] + (["-PmuzzleBenchmarkDiscoverAll=true"] if eager else []))
        ci.write_json(directory / "complete.json", {"complete": True})
        return directory

    def test_eager_layout_comparison_requires_all_commands_to_discover_all_projects(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            folder = self.fixture(root, 1, 2, {
                "baseline": 100, "control": 110, "candidate-a": 50, "candidate-b": 60,
            }, eager=True)
            identity, shared, manifests, samples, cache = slices.validate_group(folder)
            self.assertEqual(shared["discovery_mode"], "eager")
            self.assertTrue(all(sample["discovery"]["metadata_documents"] == 2 for sample in samples.values()))
            self.assertEqual(len(manifests["baseline"]), 2)
            self.assertEqual(len(manifests["candidate-a"]), 1)
            args = SimpleNamespace(inputs=root, output=root / "summary", rounds=1, partitions=[2])
            slices.aggregate(args)
            result = json.loads((args.output / "results.json").read_text())
            self.assertEqual(result["maximum_slice_paired"]["candidate"]["delta_ms"]["median"], -40)
            self.assertIn("Eager discovery on both sides", (args.output / "summary.md").read_text())
            command = folder / "candidate-b/candidate/command.json"
            original = json.loads(command.read_text())
            ci.write_json(command, [value for value in original if "muzzleBenchmarkDiscoverAll" not in value])
            with self.assertRaisesRegex(ValueError, "confirm eager discovery"):
                slices.validate_group(folder)
            ci.write_json(command, original)
            path = folder / "candidate-b/samples.json"
            raw = json.loads(path.read_text())
            raw["samples"][0]["discovery"]["projects"].pop(next(iter(samples["baseline"]["discovery"]["projects"])))
            ci.write_json(path, raw)
            with self.assertRaises(ValueError):
                slices.validate_group(folder)

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

    def test_missing_coverage_or_changed_commands_invalidate_results(self):
        for defect in ("workload", "command", "identity"):
            with self.subTest(defect=defect), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                folder = self.fixture(root, 1, 1, dict.fromkeys(slices.ROLES, 100))
                if defect == "workload":
                    path = next((folder / "candidate-b/candidate/assertions").glob("*.txt"))
                    path.write_text(path.read_text().replace("sha256", "different"))
                elif defect == "command":
                    ci.write_json(folder / "candidate-b/candidate/command.json", [":instrumentation:muzzle1", "-PmuzzleSlices=4"])
                else:
                    path = folder / "candidate-b/identity.json"
                    value = json.loads(path.read_text())
                    value["cache_snapshot_sha256"] = "different"
                    ci.write_json(path, value)
                with self.assertRaises(ValueError):
                    slices.validate_group(folder)

    def test_cache_variation_is_explicit_and_sensitivity_does_not_hide_a_cache_advantage(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            folder = self.fixture(root, 1, 2, {
                "baseline": 10000, "control": 10010, "candidate-a": 8000, "candidate-b": 7000,
            })
            for role in slices.ROLES:
                cached = role.startswith("candidate")
                path = folder / role / "samples.json"
                raw = json.loads(path.read_text())
                sample = raw["samples"][0]
                sample["task_outcomes"][":commonCompile"] = "FROM-CACHE" if cached else "EXECUTED"
                ci.write_json(path, raw)
                log = folder / role / sample["label"] / "console.log"
                if not cached:
                    log.write_text(log.read_text().replace(":commonCompile FROM-CACHE", ":commonCompile"))
                profile = folder / role / sample["label"] / "profile/profile-fixture.html"
                profile.write_text(profile.read_text() + (
                    "<h2>Task Execution</h2><table><tr><td>:commonCompile</td>"
                    f"<td>{'0.001s' if cached else '2s'}</td><td></td></tr></table>"
                ))
            (folder / "complete.json").unlink()
            args = SimpleNamespace(inputs=root, output=root / "summary", rounds=1, partitions=[2])
            slices.aggregate(args)
            result = json.loads((args.output / "results.json").read_text())
            self.assertEqual(result["maximum_slice_paired"]["candidate"]["delta_ms"]["median"], -2000)
            self.assertEqual(result["cache_sensitivity"]["candidate_paired"]["delta_ms"]["median"], 1)
            self.assertTrue(result["cache_sensitivity"]["fully_profiled"])
            self.assertEqual(len(result["groups"][0]["cache"]["variations"]), 1)
            self.assertIn("Original group-validation markers", (args.output / "summary.md").read_text())

    def test_single_artifact_report_accepts_the_actions_flattened_layout(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            folder = self.fixture(root, 1, 2, dict.fromkeys(slices.ROLES, 100))
            args = SimpleNamespace(inputs=root, output=root / "nested-summary", rounds=1, partitions=[2])
            slices.aggregate(args)
            expected = json.loads((args.output / "results.json").read_text())
            for path in folder.iterdir():
                path.rename(root / path.name)
            folder.rmdir()
            args.output = root / "flat-summary"
            slices.aggregate(args)
            self.assertEqual(json.loads((args.output / "results.json").read_text()), expected)

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
