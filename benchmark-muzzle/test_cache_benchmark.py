import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

import cache_benchmark


class CacheBenchmarkTest(unittest.TestCase):
    def test_remote_cache_requires_credentials_without_creating_a_work_directory(self):
        with patch.dict(cache_benchmark.os.environ, {}, clear=True):
            with self.assertRaisesRegex(ValueError, "DEVELOCITY_ACCESS_KEY"):
                cache_benchmark.CacheBenchmark(SimpleNamespace(backend="remote"))

    def test_remote_cache_override_leaves_included_builds_unchanged(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)

            def initialize(runner, args, *, dependency_offline):
                self.assertFalse(dependency_offline)
                runner.workload = {"seed": 42}
                runner.expected = None
                runner.revisions = {"baseline": "revision"}
                runner.init_script = root / "benchmark.init.gradle"
                runner.reports = root
                (root / "identity.json").write_text("{}\n", encoding="utf-8")

            with (
                patch.dict(cache_benchmark.os.environ, {"DEVELOCITY_ACCESS_KEY": "fixture"}),
                patch.object(
                    cache_benchmark.benchmark.Benchmark, "__init__", new=initialize,
                ),
            ):
                runner = cache_benchmark.CacheBenchmark(SimpleNamespace(
                    backend="remote", revision="revision", warm_seeds=1, rounds=5,
                ))
            self.assertEqual(runner.init_script.read_text(encoding="utf-8"), (
                "settingsEvaluated { settings ->\n"
                "  if (settings.gradle.parent == null) {\n"
                "    settings.buildCache.local.enabled = false\n"
                "    def remote = settings.buildCache.remote\n"
                "    if (remote == null) { throw new GradleException('Remote cache is missing') }\n"
                "    remote.enabled = true\n"
                "    remote.push = true\n"
                "  }\n"
                "}\n"
            ))
            self.assertTrue(json.loads(
                (root / "identity.json").read_text(encoding="utf-8"),
            )["remote_build_cache"])

    def test_cross_job_probe_requires_remote_inputs_and_no_warmup_writers(self):
        for changes in (
            {"backend": "local"}, {"inputs": None}, {"rounds": 5},
            {"warm_seeds": 1}, {"probe_namespace": ""},
        ):
            args = SimpleNamespace(
                backend="remote", inputs=Path("frozen"), rounds=1, warm_seeds=0,
                probe_namespace="earlier-namespace",
            )
            args.__dict__.update(changes)
            with (
                patch.dict(cache_benchmark.os.environ, {"DEVELOCITY_ACCESS_KEY": "fixture"}),
                self.assertRaisesRegex(ValueError, "cross-job cache probe requires"),
            ):
                cache_benchmark.CacheBenchmark(args)

    def test_cross_job_probe_rejects_misses_without_running_a_writer(self):
        for outcome in ("FROM-CACHE", "EXECUTED"):
            runner = object.__new__(cache_benchmark.CacheBenchmark)
            runner.args = SimpleNamespace(probe_namespace="earlier-namespace")
            runner.preparation_ms = 0
            runner.seed = 42
            runner.expected = {"task": "independent"}
            runner.expected_by_seed = {}
            runner.cache_samples = [{
                "label": "candidate", "assertions": {outcome: 1},
            }]
            with (
                patch.object(cache_benchmark.benchmark.Benchmark, "prepare"),
                patch.object(runner, "measured_round") as measured,
                patch.object(runner, "prepare_seed") as writer,
                patch.object(runner, "write_results") as results,
            ):
                if outcome == "EXECUTED":
                    with self.assertRaisesRegex(ValueError, "did not restore every assertion"):
                        runner.measure()
                    results.assert_not_called()
                else:
                    runner.measure()
                    results.assert_called_once_with(complete=True)
                measured.assert_called_once_with("cross-job", 1)
                writer.assert_not_called()

    def test_cache_hits_require_reports_and_up_to_date_is_not_a_fresh_checkout(self):
        runner = object.__new__(cache_benchmark.CacheBenchmark)
        task = ":example:muzzle-AssertPass-library-1.0"
        actual = {task: "verified"}
        runner.cache_assertions = True
        runner.validate_outcomes(actual, {task: "FROM-CACHE"}, preparation=False)
        runner.validate_outcomes(actual, {task: "EXECUTED"}, preparation=False)
        with self.assertRaisesRegex(ValueError, "Reported assertions"):
            runner.validate_outcomes({}, {task: "FROM-CACHE"}, preparation=False)
        with self.assertRaisesRegex(ValueError, "Invalid fresh-checkout"):
            runner.validate_outcomes(actual, {task: "UP-TO-DATE"}, preparation=False)
        runner.cache_assertions = False
        with self.assertRaisesRegex(ValueError, "Invalid fresh-checkout"):
            runner.validate_outcomes(actual, {task: "FROM-CACHE"}, preparation=False)

    def test_each_measured_command_checks_out_a_new_directory(self):
        with tempfile.TemporaryDirectory() as directory:
            runner = object.__new__(cache_benchmark.CacheBenchmark)
            runner.root = Path(directory)
            runner.source = runner.root / "source"
            runner.fresh_checkouts = 0
            runner.revisions = {"baseline": "revision"}
            runner.checkouts = {}
            with (
                patch.object(cache_benchmark.benchmark, "run") as child,
                patch.object(runner, "build") as build,
            ):
                runner.fresh_build("baseline", "first")
                first = runner.checkouts["baseline"]
                runner.fresh_build("baseline", "second")
                self.assertNotEqual(first, runner.checkouts["baseline"])
                self.assertEqual(child.call_count, 4)
                self.assertEqual(build.call_count, 2)
                self.assertTrue(all(call.kwargs["offline"] for call in build.call_args_list))

    def test_seed_specific_expected_records_cannot_leak_between_samples(self):
        runner = object.__new__(cache_benchmark.CacheBenchmark)
        runner.seed = 42
        runner.expected_by_seed = {42: {"a": "first"}, 43: {"b": "second"}}

        def build(*args, **kwargs):
            self.assertEqual(runner.expected, runner.expected_by_seed[runner.seed])
            return {"total execution time": 1}, {}

        with patch.object(cache_benchmark.benchmark.Benchmark, "build", side_effect=build):
            runner.build("baseline", "first", offline=True)
            runner.seed = 43
            runner.build("candidate", "second", offline=True)

    def test_empty_namespaces_do_not_warm_the_random_sample_cache(self):
        runner = object.__new__(cache_benchmark.CacheBenchmark)
        runner.namespace = "research"
        runner.seed = 42
        task = ":example:muzzle-AssertPass-library-1.0"
        runner.expected_by_seed = {42: {task: "verified"}}
        runner.prior_manifests = {}
        runner.writers = 0
        runner.cache_samples = []
        with (
            patch.object(runner, "fresh_build", return_value=(
                {"total execution time": 1}, {task: "EXECUTED"},
            )),
            patch.object(runner, "write_results"),
        ):
            runner.measured_round("empty", 1)
            runner.measured_round("empty", 2)
            self.assertEqual(runner.prior_manifests, {})
            self.assertEqual(runner.writers, 0)
            self.assertNotEqual(
                runner.cache_samples[0]["namespace"], runner.cache_samples[3]["namespace"],
            )

    def test_empty_cache_cannot_report_a_cache_hit(self):
        runner = object.__new__(cache_benchmark.CacheBenchmark)
        runner.namespace = "research"
        runner.seed = 42
        task = ":example:muzzle-AssertPass-library-1.0"
        runner.expected_by_seed = {42: {task: "verified"}}
        runner.prior_manifests = {}
        runner.writers = 0
        runner.cache_samples = []
        with (
            patch.object(runner, "fresh_build", return_value=(
                {"total execution time": 1}, {task: "FROM-CACHE"},
            )),
            patch.object(runner, "write_results"),
            self.assertRaisesRegex(ValueError, "isolated empty assertion cache"),
        ):
            runner.measured_round("empty", 1)

    def test_same_sample_requires_cache_restoration_and_retains_misses(self):
        for outcome in ("EXECUTED", "FROM-CACHE"):
            runner = object.__new__(cache_benchmark.CacheBenchmark)
            runner.namespace = "research"
            runner.seed = 42
            task = ":example:muzzle-AssertPass-library-1.0"
            runner.expected_by_seed = {42: {task: "verified"}}
            runner.prior_manifests = {task: "verified"}
            runner.writers = 1
            runner.cache_samples = []
            with (
                patch.object(runner, "fresh_build", side_effect=[
                    ({"total execution time": 1}, {task: "EXECUTED"}),
                    ({"total execution time": 1}, {task: "EXECUTED"}),
                    ({"total execution time": 1}, {task: outcome}),
                ]),
                patch.object(runner, "write_results") as results,
            ):
                if outcome == "EXECUTED":
                    with self.assertRaisesRegex(ValueError, "same-sample cache did not restore"):
                        runner.measured_round("same", 1)
                else:
                    runner.measured_round("same", 1)
                self.assertEqual(len(runner.cache_samples), 3)
                self.assertEqual(runner.cache_samples[-1]["assertions"], {outcome: 1})
                self.assertEqual(results.call_count, 3)

    def test_writers_are_checked_against_an_uncached_reference(self):
        runner = object.__new__(cache_benchmark.CacheBenchmark)
        runner.preparation_ms = 0
        runner.prior_manifests = {}
        runner.writers = 0
        calls = []

        def build(label, name, *, offline, preparation):
            calls.append((label, offline, runner.cache_assertions))
            if not runner.cache_assertions and offline:
                runner.expected = {"task": "independent"}
            if runner.cache_assertions:
                self.assertEqual(runner.expected, {"task": "independent"})

        with patch.object(runner, "build", side_effect=build):
            runner.prepare_seed(42, "writer", cache=True)
        self.assertEqual(calls, [
            ("baseline", True, False),
            ("candidate", True, True),
        ])
        self.assertEqual(runner.prior_manifests, {"task": "independent"})
        self.assertEqual(runner.writers, 1)


if __name__ == "__main__":
    unittest.main()
