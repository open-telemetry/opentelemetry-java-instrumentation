import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import benchmark_support as benchmark


class BenchmarkSupportTest(unittest.TestCase):
    def test_order_balances_baseline_and_candidate(self):
        self.assertEqual(benchmark.order(0), ("baseline", "control", "candidate"))
        self.assertEqual(benchmark.order(1), ("candidate", "control", "baseline"))
        self.assertEqual(benchmark.order(2), benchmark.order(0))

    def test_manifests_compare_contents_not_only_assertion_counts(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            task = ":instrumentation:example:muzzle-AssertPass-library-1.0"
            report = root / "assertion.txt"
            report.write_text(f"{task}\nassertPass\ttrue\nartifact\t0\tlibrary.jar\tabc\n")
            expected = benchmark.manifests(root)
            report.write_text(f"{task}\nassertPass\ttrue\nartifact\t0\tlibrary.jar\tdef\n")
            with self.assertRaisesRegex(ValueError, "workload mismatch"):
                benchmark.require_equal(expected, benchmark.manifests(root), "workload")

    def test_empty_invalid_and_duplicate_manifests_fail(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with self.assertRaises(ValueError):
                benchmark.manifests(root)
            path = root / "first.txt"
            for text in ("", ":compileJava\n"):
                path.write_text(text)
                with self.assertRaises(ValueError):
                    benchmark.manifests(root)
            for name in ("first.txt", "second.txt"):
                (root / name).write_text(":example:muzzle-AssertPass-library\n")
            with self.assertRaises(ValueError):
                benchmark.manifests(root)

    def test_task_outcomes_distinguish_executed_and_cached(self):
        self.assertEqual(benchmark.task_outcomes(
            "> Task :compileJava FROM-CACHE\n"
            "> Task :byteBuddyJava UP-TO-DATE\n"
            "> Task :example:muzzle-AssertPass-library-1.0\n"
        ), {
            ":compileJava": "FROM-CACHE",
            ":byteBuddyJava": "UP-TO-DATE",
            ":example:muzzle-AssertPass-library-1.0": "EXECUTED",
        })
        for log in (
            "BUILD SUCCESSFUL",
            "> Task :compileJava\n> Task :compileJava FROM-CACHE\n",
            "> Task :example:muzzle-AssertPass-library\n> Task :example:muzzle-AssertPass-library\n",
        ):
            with self.assertRaises(ValueError):
                benchmark.task_outcomes(log)

    def test_repeated_compiler_headers_do_not_count_as_extra_executions(self):
        self.assertEqual(benchmark.task_outcomes(
            "> Task :compileKotlin\n"
            "> Task :compileJava NO-SOURCE\n"
            "> Task :compileKotlin\n"
            "w: Language version 2.0 is deprecated.\n"
        ), {":compileKotlin": "EXECUTED", ":compileJava": "NO-SOURCE"})

    def test_inventory_detects_changed_metadata(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            path = root / "maven-metadata.xml"
            path.write_text("<version>1.0</version>")
            expected = benchmark.inventory(root)
            path.write_text("<version>2.0</version>")
            with self.assertRaises(ValueError):
                benchmark.require_equal(expected, benchmark.inventory(root), "metadata")

    def test_fixture_identity_rejects_source_changes_but_allows_build_logic_changes(self):
        source = b"100644 blob abc\tinstrumentation/example/src/main/java/Example.java\0"
        build = b"100644 blob def\tinstrumentation/build.gradle.kts\0"
        expected = benchmark.fixture_identity(source + build)
        self.assertEqual(expected, benchmark.fixture_identity(build.replace(b"def", b"ghi") + source))
        self.assertNotEqual(expected, benchmark.fixture_identity(source.replace(b"abc", b"xyz") + build))
        with self.assertRaises(ValueError):
            benchmark.fixture_identity(build)

    def test_subprocesses_are_hidden_on_windows_and_failures_propagate(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(benchmark.subprocess, "run") as child:
            child.return_value.stdout = b"output"
            self.assertEqual(benchmark.run(["java", "--version"]), b"output")
            benchmark.run(["java", "--version"], log=Path(directory) / "console.log")
            for call in child.call_args_list:
                self.assertTrue(call.kwargs["check"])
                if os.name == "nt":
                    self.assertEqual(call.kwargs["creationflags"], subprocess.CREATE_NO_WINDOW)
            child.side_effect = subprocess.CalledProcessError(1, ["java"])
            with self.assertRaises(subprocess.CalledProcessError):
                benchmark.run(["java", "--version"])


if __name__ == "__main__":
    unittest.main()
