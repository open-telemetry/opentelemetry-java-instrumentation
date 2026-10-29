import csv
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

    def test_measurements_require_one_real_wall_clock_sample(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "benchmark.csv"
            self.write_csv(path, [
                ["MEASURE", "1", "total execution time", "120.50"],
                ["MEASURE", "1", "task start", "80.20"],
                ["WARM_UP", "1", "total execution time", "999"],
            ])
            self.assertEqual(benchmark.measurements(path), {
                "total execution time": 120.5, "task start": 80.2,
            })
            for rows in (
                [["MEASURE", "1", "total execution time", "NaN"]],
                [["MEASURE", "1", "total execution time", "-1"]],
                [["MEASURE", "2", "total execution time", "120"]],
                [["MEASURE", "1", "total execution time", "120"]],
            ):
                with self.subTest(rows=rows):
                    self.write_csv(path, rows)
                    with self.assertRaises(ValueError):
                        benchmark.measurements(path)

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
            runner.snapshots = runner.root / "snapshots"
            with self.assertRaises(ValueError):
                runner.remove(runner.root)
            with self.assertRaises(ValueError):
                runner.remove(runner.root / "source")
            checkout = runner.root / "checkout"
            checkout.mkdir()
            runner.remove(checkout)
            self.assertFalse(checkout.exists())

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

    def test_invocations_are_offline_and_reject_invalid_measurements(self):
        for failure in (
            "none", "configuration-reused", "changed-metadata", "changed-workload",
            "compile-cache-miss",
        ):
            with self.subTest(failure=failure), tempfile.TemporaryDirectory() as directory:
                runner = object.__new__(benchmark.Benchmark)
                runner.root = Path(directory)
                runner.checkout = runner.root / "checkout"
                runner.repository = runner.root / "metadata"
                runner.audit = runner.root / "audit"
                runner.profiler_output = runner.root / "profiler-output"
                runner.snapshots = runner.root / "snapshots"
                runner.home = runner.root / "gradle-home"
                runner.args = SimpleNamespace(profiler=runner.root / "profiler")
                runner.init_script = runner.root / "benchmark.init.gradle"
                runner.env = {}
                runner.workload = {"seed": 1729, "versions_limit": 10}
                runner.task = ":instrumentation:muzzle1"
                runner.checkout.mkdir()
                runner.repository.mkdir()
                (runner.repository / "metadata.xml").write_text("frozen", encoding="utf-8")
                runner.frozen_metadata = benchmark.inventory(runner.repository)
                task = ":example:muzzle-AssertPass-library"
                record = f"{task}\nassertPass\ttrue\n"
                runner.expected = {task: record}
                configuration_cache = runner.checkout / ".gradle" / "configuration-cache"
                configuration_cache.mkdir(parents=True)
                (configuration_cache / "state").write_text("stale", encoding="utf-8")

                def fake_profiler(command, **kwargs):
                    self.assertIn("--single-shot", command)
                    self.assertIn("--no-daemon", command)
                    self.assertFalse(configuration_cache.exists())
                    scenario = (runner.root / "muzzle.scenarios").read_text(encoding="utf-8")
                    self.assertIn('"--offline"', scenario)
                    self.assertIn("muzzleBenchmarkRepositoryOffline=true", scenario)
                    runner.profiler_output.mkdir()
                    runner.audit.mkdir()
                    self.write_csv(runner.profiler_output / "benchmark.csv", [
                        ["MEASURE", "1", "total execution time", "120.50"],
                        ["MEASURE", "1", "task start", "80.20"],
                    ])
                    log = "Configuration cache entry stored.\n" + f"> Task {task}\n"
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

                with patch.object(benchmark, "run", side_effect=fake_profiler):
                    if failure == "none":
                        metrics, tasks = runner.invoke_profiler(runner.root / "reports" / "sample")
                        self.assertEqual(metrics["total execution time"], 120.5)
                        self.assertEqual(tasks, {task: "EXECUTED"})
                    else:
                        with self.assertRaises(ValueError):
                            runner.invoke_profiler(runner.root / "reports" / "sample")

    @staticmethod
    def write_csv(path, rows):
        with path.open("w", encoding="utf-8", newline="") as stream:
            writer = csv.writer(stream)
            writer.writerow(["Phase", "Iteration", "Sample", "Duration"])
            writer.writerows(rows)


if __name__ == "__main__":
    unittest.main()
