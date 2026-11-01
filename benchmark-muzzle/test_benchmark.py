import json
import os
from pathlib import Path
import subprocess
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

import benchmark


class BenchmarkTest(unittest.TestCase):
    def test_work_directory_cannot_overlap_the_source(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for work_dir in (root, root / "inside"):
                with self.subTest(work_dir=work_dir), self.assertRaises(ValueError):
                    benchmark.Benchmark(SimpleNamespace(repository=root, work_dir=work_dir))

    def test_order_balances_baseline_and_candidate(self):
        self.assertEqual(benchmark.order(0), ("baseline", "control", "candidate"))
        self.assertEqual(benchmark.order(1), ("candidate", "control", "baseline"))
        self.assertEqual(benchmark.order(2), benchmark.order(0))

    def test_manifests_include_contents_not_just_assertion_counts(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            task = ":instrumentation:example:javaagent:muzzle-AssertPass-example-1.0"
            (root / "assertion.txt").write_text(
                f"{task}\nassertPass\ttrue\nartifact\tlibrary:1.0\tlibrary.jar\tabc\n",
                encoding="utf-8",
            )
            expected = benchmark.manifests(root)
            (root / "assertion.txt").write_text(
                f"{task}\nassertPass\ttrue\nartifact\tlibrary:1.0\tlibrary.jar\tdef\n",
                encoding="utf-8",
            )
            with self.assertRaisesRegex(ValueError, "workload mismatch"):
                benchmark.require_equal(expected, benchmark.manifests(root), "workload")

    def test_empty_and_duplicate_manifests_fail(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with self.assertRaises(ValueError):
                benchmark.manifests(root)
            for name in ("first.txt", "second.txt"):
                (root / name).write_text(":example:muzzle-AssertPass-example\n", encoding="utf-8")
            with self.assertRaises(ValueError):
                benchmark.manifests(root)

    def test_task_outcomes_distinguish_executed_and_cached(self):
        self.assertEqual(benchmark.task_outcomes(
            "> Task :compileJava FROM-CACHE\n"
            "> Task :byteBuddyJava UP-TO-DATE\n"
            "> Task :example:muzzle-AssertPass-example-1.0\n"
        ), {
            ":compileJava": "FROM-CACHE",
            ":byteBuddyJava": "UP-TO-DATE",
            ":example:muzzle-AssertPass-example-1.0": "EXECUTED",
        })
        with self.assertRaises(ValueError):
            benchmark.task_outcomes("BUILD SUCCESSFUL")

    def test_inventory_detects_changed_metadata(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            path = root / "maven-metadata.xml"
            path.write_text("<version>1.0</version>", encoding="utf-8")
            expected = benchmark.inventory(root)
            path.write_text("<version>2.0</version>", encoding="utf-8")
            with self.assertRaises(ValueError):
                benchmark.require_equal(expected, benchmark.inventory(root), "metadata")

    def test_fixture_identity_rejects_source_changes_but_allows_build_logic_changes(self):
        source = b"100644 blob abc\tinstrumentation/example/src/main/java/Example.java\0"
        build = b"100644 blob def\tinstrumentation/build.gradle.kts\0"
        expected = benchmark.fixture_identity(source + build)
        self.assertEqual(expected, benchmark.fixture_identity(build.replace(b"def", b"ghi") + source))
        self.assertNotEqual(
            expected, benchmark.fixture_identity(source.replace(b"abc", b"xyz") + build),
        )
        with self.assertRaises(ValueError):
            benchmark.fixture_identity(build)

    def test_subprocesses_are_hidden_on_windows_and_failures_propagate(self):
        with patch.object(benchmark.subprocess, "run") as child:
            child.return_value.stdout = b"output"
            self.assertEqual(benchmark.run(["java", "--version"]), b"output")
            arguments = child.call_args.kwargs
            self.assertTrue(arguments["check"])
            if os.name == "nt":
                self.assertEqual(arguments["creationflags"], subprocess.CREATE_NO_WINDOW)
            child.side_effect = subprocess.CalledProcessError(1, ["java"])
            with self.assertRaises(subprocess.CalledProcessError):
                benchmark.run(["java", "--version"])

    def test_cleanup_refuses_source_or_root_directories(self):
        with tempfile.TemporaryDirectory() as directory:
            runner = object.__new__(benchmark.Benchmark)
            runner.root = Path(directory)
            runner.checkouts = {
                label: runner.root / label for label in ("baseline", "candidate")
            }
            runner.audit = runner.root / "audit"
            with self.assertRaises(ValueError):
                runner.remove(runner.root)
            with self.assertRaises(ValueError):
                runner.remove(runner.root / "source")
            for checkout in runner.checkouts.values():
                checkout.mkdir()
                with self.assertRaises(ValueError):
                    runner.remove(checkout)
            runner.audit.mkdir()
            runner.remove(runner.audit)
            self.assertFalse(runner.audit.exists())

    def test_summary_retains_all_samples(self):
        samples = [
            {"label": label, "metrics": {"total execution time": duration}}
            for label in ("baseline", "control", "candidate")
            for duration in (10, 11, 1000)
        ]
        result = benchmark.summary(samples)
        self.assertEqual(result["baseline"]["total execution time"], {
            "median_ms": 11, "min_ms": 10, "max_ms": 1000, "samples": 3,
            "stdev_ms": benchmark.statistics.stdev([10, 11, 1000]),
        })

    def test_completed_report_identifies_functional_runs_and_unresolved_dependencies(self):
        with tempfile.TemporaryDirectory() as directory:
            runner = object.__new__(benchmark.Benchmark)
            runner.reports = Path(directory)
            runner.args = SimpleNamespace(partition=1, rounds=1)
            runner.started = benchmark.time.perf_counter() - 3
            runner.setup_ms = 100
            runner.preparation_ms = 200
            runner.dependency_offline = True
            runner.phases = [
                {"name": f"round-1-{label}", "elapsed_ms": 100}
                for label in benchmark.order(0)
            ]
            runner.expected = {
                "first": "assertPass\ttrue\nunresolved\tmissing dependency\n",
                "second": "assertPass\tfalse\n",
            }
            runner.samples = [
                {"round": 1, "label": label, "revision": "fixture",
                 "metrics": {"total execution time": 100}}
                for label in benchmark.order(0)
            ]
            runner.write_results(complete=True)
            text = (runner.reports / "summary.md").read_text(encoding="utf-8")
            self.assertIn("Assertions with unresolved dependencies: 1.", text)
            self.assertIn("Functional run only", text)
            self.assertTrue((runner.reports / "statistics.json").is_file())
            self.assertEqual(len((runner.reports / "samples.csv").read_text().splitlines()), 4)
            timing = json.loads((runner.reports / "timing.json").read_text(encoding="utf-8"))
            self.assertEqual(timing["setup_ms"], 100)
            self.assertEqual(timing["preparation_ms"], 200)
            self.assertEqual(timing["measured_gradle_ms"], 300)
            self.assertAlmostEqual(
                sum(timing[key] for key in ("setup_ms", "preparation_ms", "measured_gradle_ms", "overhead_ms")),
                timing["total_ms"],
            )
            runner.dependency_offline = False
            runner.live_metadata_samples = [
                {"label": label, "metrics": {"total execution time": 100},
                 "metadata": {"metadata.xml": label}}
                for label in ("baseline", "candidate")
            ]
            runner.phases.extend(
                {"name": f"live-metadata-{label}", "elapsed_ms": 100}
                for label in ("baseline", "candidate")
            )
            runner.write_results(complete=True)
            text = (runner.reports / "summary.md").read_text(encoding="utf-8")
            self.assertIn("Gradle dependency resolution is online", text)
            self.assertIn("| baseline | 0.10s | 1 |", text)
            timing = json.loads((runner.reports / "timing.json").read_text(encoding="utf-8"))
            self.assertEqual(timing["measured_gradle_ms"], 500)
            self.assertEqual(len(json.loads(
                (runner.reports / "samples.json").read_text(encoding="utf-8"),
            )["samples"]), 3)

    def test_preparation_warms_fixed_checkouts_before_offline_audits(self):
        with tempfile.TemporaryDirectory() as directory:
            runner = object.__new__(benchmark.Benchmark)
            runner.args = SimpleNamespace(online_dependencies=False)
            runner.root = Path(directory)
            for name in ("source", "home", "repository", "audit", "reports"):
                setattr(runner, name, runner.root / name)
            runner.checkouts = {
                label: runner.root / label for label in ("baseline", "candidate")
            }
            for path in (runner.source, runner.home, runner.reports):
                path.mkdir()
            runner.revisions = {"baseline": "before", "candidate": "after"}
            runner.frozen_metadata = None
            runner.input_identity = {"seed": 1729}
            phases = []

            def fake_git(command, **kwargs):
                if "clone" in command:
                    checkout = Path(command[-1])
                    checkout.mkdir()
                    control = checkout / benchmark.CONTROLS
                    control.parent.mkdir(parents=True, exist_ok=True)
                    control.write_bytes(b"support")
                return b""

            def build(label, name, *, offline, preparation):
                phases.append(name)
                self.assertTrue(preparation)
                self.assertTrue(all(
                    (checkout / benchmark.CONTROLS).is_file()
                    for checkout in runner.checkouts.values()
                ))
                self.assertEqual(offline, name.startswith("warmup"))
                if label == "candidate" or offline:
                    self.assertIsNotNone(runner.frozen_metadata)
                runner.repository.mkdir(exist_ok=True)
                (runner.repository / "metadata.xml").write_text("frozen", encoding="utf-8")
                runner.remove(runner.audit)
                runner.audit.mkdir()
                (runner.audit / "assertion.txt").write_text("verified", encoding="utf-8")

            with (
                patch.object(benchmark, "run", side_effect=fake_git),
                patch.object(runner, "build", side_effect=build),
            ):
                runner.prepare()
            self.assertEqual(phases, [
                "bootstrap-baseline", "bootstrap-candidate", "warmup-baseline", "warmup-candidate",
            ])
            self.assertTrue(all(path.is_dir() for path in runner.checkouts.values()))
            self.assertTrue((runner.reports / "inputs" / "identity.json").is_file())

    def test_preparation_rejects_missing_or_different_support_before_building(self):
        for failure in ("missing-baseline", "missing-candidate", "different"):
            with self.subTest(failure=failure), tempfile.TemporaryDirectory() as directory:
                runner = object.__new__(benchmark.Benchmark)
                runner.root = Path(directory)
                runner.source = runner.root / "source"
                runner.checkouts = {
                    label: runner.root / label for label in ("baseline", "candidate")
                }
                runner.revisions = {"baseline": "before", "candidate": "after"}

                def fake_git(command, **kwargs):
                    if "clone" in command:
                        checkout = Path(command[-1])
                        checkout.mkdir()
                        if failure != f"missing-{checkout.name}":
                            control = checkout / benchmark.CONTROLS
                            control.parent.mkdir(parents=True, exist_ok=True)
                            control.write_bytes(
                                checkout.name.encode() if failure == "different" else b"support"
                            )
                    return b""

                with (
                    patch.object(benchmark, "run", side_effect=fake_git),
                    patch.object(runner, "build") as build,
                    self.assertRaisesRegex(ValueError, "lacks benchmark support|Benchmark controls mismatch"),
                ):
                    runner.prepare()
                build.assert_not_called()

    def test_online_preparation_needs_only_one_reference_build_per_revision(self):
        with tempfile.TemporaryDirectory() as directory:
            runner = object.__new__(benchmark.Benchmark)
            runner.root = Path(directory)
            runner.source = runner.root / "source"
            runner.reports = runner.root / "reports"
            runner.repository = runner.root / "metadata"
            runner.audit = runner.root / "audit"
            runner.reports.mkdir()
            runner.args = SimpleNamespace(online_dependencies=True)
            runner.checkouts = {
                label: runner.root / label for label in ("baseline", "candidate")
            }
            runner.revisions = {"baseline": "before", "candidate": "after"}
            runner.frozen_metadata = None
            runner.expected = None
            runner.input_identity = {"seed": 1729}
            record = ":example:muzzle-AssertPass-library-1.0\nassertPass\ttrue\n"
            phases = []

            def fake_git(command, **kwargs):
                if "clone" in command:
                    checkout = Path(command[-1])
                    control = checkout / benchmark.CONTROLS
                    control.parent.mkdir(parents=True)
                    control.write_bytes(b"support")
                return b""

            def build(label, name, *, offline, preparation):
                phases.append((label, offline))
                self.assertTrue(preparation)
                if label == "candidate":
                    self.assertEqual(runner.expected, {
                        ":example:muzzle-AssertPass-library-1.0": record,
                    })
                runner.repository.mkdir(exist_ok=True)
                (runner.repository / "metadata.xml").write_text("frozen", encoding="utf-8")
                runner.audit.mkdir(exist_ok=True)
                (runner.audit / "assertion.txt").write_text(record, encoding="utf-8")

            with (
                patch.object(benchmark, "run", side_effect=fake_git),
                patch.object(runner, "build", side_effect=build),
            ):
                runner.prepare()
            self.assertEqual(phases, [("baseline", False), ("candidate", True)])
            self.assertEqual(benchmark.manifests(runner.reports / "inputs" / "assertions"), runner.expected)

    def test_live_metadata_probe_checks_reports_and_restores_state_on_failure(self):
        for mismatch in (False, True):
            with self.subTest(mismatch=mismatch), tempfile.TemporaryDirectory() as directory:
                runner = object.__new__(benchmark.Benchmark)
                runner.root = Path(directory)
                runner.repository = runner.root / "frozen"
                runner.frozen_metadata = {"metadata.xml": "frozen"}
                runner.audit = runner.root / "audit"
                runner.reports = runner.root / "reports"
                runner.reports.mkdir()
                record = ":example:muzzle-AssertPass-library-1.0\nassertPass\ttrue\n"
                runner.expected = {":example:muzzle-AssertPass-library-1.0": record}
                repository, frozen = runner.repository, runner.frozen_metadata

                def build(label, name, *, offline):
                    self.assertFalse(offline)
                    self.assertIsNone(runner.frozen_metadata)
                    self.assertFalse(runner.repository.exists())
                    runner.repository.mkdir()
                    (runner.repository / "metadata.xml").write_text(label, encoding="utf-8")
                    runner.audit.mkdir(exist_ok=True)
                    (runner.audit / "assertion.txt").write_text(
                        record + ("changed" if mismatch and label == "candidate" else ""),
                        encoding="utf-8",
                    )
                    return {"total execution time": 100}, {}

                with patch.object(runner, "build", side_effect=build):
                    if mismatch:
                        with self.assertRaisesRegex(ValueError, "Live assertion/dependency workload"):
                            runner.measure_live_metadata()
                    else:
                        runner.measure_live_metadata()
                self.assertEqual(runner.repository, repository)
                self.assertIs(runner.frozen_metadata, frozen)
                samples = json.loads(
                    (runner.reports / "live-metadata-samples.json").read_text(encoding="utf-8"),
                )
                self.assertEqual(len(samples), 2)
                self.assertEqual(set(samples[0]["metadata"]), {"metadata.xml"})
                self.assertNotEqual(samples[0]["metadata"], samples[1]["metadata"])

    def test_measurement_loop_does_not_copy_caches_or_checkouts(self):
        runner = object.__new__(benchmark.Benchmark)
        runner.args = SimpleNamespace(rounds=2)
        runner.revisions = {"baseline": "before", "candidate": "after"}
        runner.samples = []
        with (
            patch.object(runner, "prepare"),
            patch.object(runner, "write_results"),
            patch.object(runner, "build", return_value=({"total execution time": 100}, {})) as build,
            patch.object(benchmark.shutil, "copytree") as copy,
            patch.object(benchmark, "run") as child,
        ):
            runner.measure()
            copy.assert_not_called()
            child.assert_not_called()
            self.assertEqual([call.args[:2] for call in build.call_args_list], [
                ("baseline", "round-1-baseline"), ("baseline", "round-1-control"),
                ("candidate", "round-1-candidate"), ("candidate", "round-2-candidate"),
                ("baseline", "round-2-control"), ("baseline", "round-2-baseline"),
            ])
            self.assertEqual(len(runner.samples), 6)
            runner.write_results.assert_called_with(complete=True)

    def test_offline_build_requires_frozen_aether_metadata(self):
        runner = object.__new__(benchmark.Benchmark)
        runner.frozen_metadata = None
        with self.assertRaisesRegex(ValueError, "captured metadata snapshot"):
            runner.build("baseline", "sample", offline=True)

    def test_direct_builds_are_offline_timed_and_reject_invalid_measurements(self):
        for failure in (
            "none", "online-dependencies", "configuration-reused", "changed-metadata", "changed-workload",
            "compile-cache-miss", "configuration-not-stored", "assertion-not-executed",
            "command-failed",
        ):
            with self.subTest(failure=failure), tempfile.TemporaryDirectory() as directory:
                runner = object.__new__(benchmark.Benchmark)
                runner.root = Path(directory)
                runner.checkouts = {
                    label: runner.root / label for label in ("baseline", "candidate")
                }
                runner.repository = runner.root / "metadata"
                runner.audit = runner.root / "audit"
                runner.home = runner.root / "gradle-home"
                runner.reports = runner.root / "reports"
                runner.phases = []
                runner.init_script = runner.root / "benchmark.init.gradle"
                runner.env = {}
                runner.dependency_offline = failure != "online-dependencies"
                runner.workload = {"seed": 1729, "versions_limit": 10}
                runner.task = ":instrumentation:muzzle1"
                checkout = runner.checkouts["baseline"]
                checkout.mkdir()
                runner.repository.mkdir()
                (runner.repository / "metadata.xml").write_text("frozen", encoding="utf-8")
                runner.frozen_metadata = benchmark.inventory(runner.repository)
                task = ":example:muzzle-AssertPass-library"
                record = f"{task}\nassertPass\ttrue\n"
                runner.expected = {task: record}
                configuration_cache = checkout / ".gradle" / "configuration-cache"
                configuration_cache.mkdir(parents=True)
                (configuration_cache / "state").write_text("stale", encoding="utf-8")

                def fake_gradle(command, **kwargs):
                    self.assertEqual(command[0], str(checkout / "gradlew"))
                    self.assertEqual(command[1], runner.task)
                    self.assertIn("--no-daemon", command)
                    self.assertIn("--configuration-cache", command)
                    self.assertEqual("--offline" in command, runner.dependency_offline)
                    self.assertIn("--no-scan", command)
                    self.assertIn("-PmuzzleBenchmarkRepositoryOffline=true", command)
                    self.assertNotIn("--stop", command)
                    self.assertNotIn("help", command)
                    self.assertEqual(kwargs["cwd"], checkout)
                    self.assertFalse(configuration_cache.exists())
                    runner.audit.mkdir()
                    log = "Configuration cache entry stored.\n"
                    if failure == "configuration-not-stored":
                        log = ""
                    log += f"> Task {task}" + (" UP-TO-DATE" if failure == "assertion-not-executed" else "") + "\n"
                    if failure == "configuration-reused":
                        log += "Reusing configuration cache.\n"
                    if failure == "compile-cache-miss":
                        log += "> Task :example:compileJava FROM-CACHE\n"
                    kwargs["log"].write_text(log, encoding="utf-8")
                    if failure == "changed-metadata":
                        (runner.repository / "metadata.xml").write_text("changed", encoding="utf-8")
                    (runner.audit / "task.txt").write_text(
                        record + ("changed" if failure == "changed-workload" else ""),
                        encoding="utf-8",
                    )
                    if failure == "command-failed":
                        raise subprocess.CalledProcessError(1, command)

                with (
                    patch.object(benchmark, "run", side_effect=fake_gradle) as child,
                    patch.object(benchmark.time, "perf_counter", side_effect=[10.0, 11.25]),
                ):
                    if failure in ("none", "online-dependencies"):
                        metrics, tasks = runner.build("baseline", "round-1-baseline", offline=True)
                        self.assertEqual(metrics, {"total execution time": 1250})
                        self.assertEqual(tasks, {task: "EXECUTED"})
                        child.assert_called_once()
                    else:
                        with self.assertRaises(
                            subprocess.CalledProcessError if failure == "command-failed" else ValueError
                        ):
                            runner.build("baseline", "round-1-baseline", offline=True)
                self.assertEqual(runner.phases[0]["elapsed_ms"], 1250)
                self.assertEqual(runner.phases[0]["process_success"], failure != "command-failed")


if __name__ == "__main__":
    unittest.main()
