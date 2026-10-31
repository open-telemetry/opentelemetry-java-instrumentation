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

    def test_writers_are_checked_against_an_uncached_offline_reference(self):
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
            ("baseline", False, False),
            ("baseline", True, False),
            ("candidate", True, True),
        ])
        self.assertEqual(runner.prior_manifests, {"task": "independent"})
        self.assertEqual(runner.writers, 1)


if __name__ == "__main__":
    unittest.main()
