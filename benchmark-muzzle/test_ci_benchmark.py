import io
import json
import os
from pathlib import Path
import subprocess
import tarfile
import tempfile
import time
from types import SimpleNamespace
import unittest
from unittest.mock import patch

import benchmark
import ci_benchmark as ci


ASSERTION = ":instrumentation:example:muzzle-AssertPass-fixture-library-1.0"
RECORD = ASSERTION + "\nassertPass\ttrue\nartifact\t0\tfixture:library:1.0\tlibrary.jar\tsha256\n"
PROFILE = f"""
<h2>Summary</h2><table>
<tr><td>Total Build Time</td><td>1m2.345s</td></tr>
<tr><td>Configuring Projects</td><td>2.345s</td></tr>
<tr><td>Task Execution</td><td>1s</td></tr></table>
<h2>Task Execution</h2><table>
<tr><td>:</td><td>1s</td><td>(total)</td></tr>
<tr><td>{ASSERTION}</td><td>0.5s</td><td></td></tr></table>
"""


def build_log(assertion=ASSERTION):
    return (
        "MUZZLE_BENCHMARK_REMOTE enabled=true push=false\n"
        f"MUZZLE_BENCHMARK_TASK_GRAPH {int(time.time() * 1000)}\n"
        f"> Task {assertion}\nBUILD SUCCESSFUL in 1s\nConfiguration cache entry stored.\n"
    )


def write_record(directory, record=RECORD):
    directory.mkdir(parents=True, exist_ok=True)
    task = record.splitlines()[0]
    (directory / (benchmark.hashlib.sha256(task.encode()).hexdigest() + ".txt")).write_text(
        record, encoding="utf-8",
    )


class CiBenchmarkTest(unittest.TestCase):
    def snapshot(self, root):
        source = root / "seed"
        (source / "caches").mkdir(parents=True)
        (source / "caches" / "value").write_text("original", encoding="utf-8")
        (source / "caches" / "held.lock").write_text("lock", encoding="utf-8")
        archive = root / "snapshot.tar.gz"
        digest = ci.snapshot(source, archive)
        return archive, digest

    def runner(self, root):
        runner = object.__new__(ci.Comparison)
        runner.root = root / "run"
        runner.root.mkdir()
        runner.reports = runner.root / "reports"
        runner.reports.mkdir()
        runner.source = root / "source"
        runner.source.mkdir()
        runner.snapshot, _ = self.snapshot(root)
        runner.args = SimpleNamespace(partition=1, round=1)
        runner.revisions = {"baseline": "before", "candidate": "after"}
        runner.samples = []
        runner.env = dict(os.environ)
        runner.identity = {"workload": {"seed": 1729, "versions_limit": 10}}
        return runner

    def fake_build(self, command, *, cwd=None, env=None, log=None):
        if command[:2] == ["git", "clone"]:
            Path(command[-1]).mkdir()
        elif log is not None:
            checkout = Path(cwd)
            home = Path(env["GRADLE_USER_HOME"])
            self.assertEqual((home / "caches" / "value").read_text(), "original")
            self.assertFalse((home / "caches" / "held.lock").exists())
            self.assertFalse((checkout / "build").exists())
            self.assertNotIn("--offline", command)
            self.assertNotIn("--no-build-cache", command)
            self.assertFalse(any("muzzleBenchmarkRepository" in arg for arg in command))
            self.assertIn("--no-daemon", command)
            (home / "caches" / "value").write_text("modified")
            audit = Path(next(arg.split("=", 1)[1] for arg in command if arg.startswith("-PmuzzleBenchmarkReportDirectory=")))
            write_record(audit)
            log.write_text(build_log(), encoding="utf-8")
            profile = checkout / "build" / "reports" / "profile"
            profile.mkdir(parents=True)
            (profile / "profile-fixture.html").write_text(PROFILE, encoding="utf-8")
            metadata = checkout / "instrumentation" / "example" / "build" / "muzzleRepo"
            metadata.mkdir(parents=True)
            (metadata / "maven-metadata-fixture.xml").write_text("<metadata/>", encoding="utf-8")
        return b""

    def test_snapshot_restores_independent_mutable_homes_and_excludes_locks(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            archive, digest = self.snapshot(root)
            first, second = root / "first", root / "second"
            ci.restore_snapshot(archive, first)
            (first / "caches" / "value").write_text("changed")
            ci.restore_snapshot(archive, second)
            self.assertEqual((second / "caches" / "value").read_text(), "original")
            self.assertEqual((root / "seed" / "caches" / "value").read_text(), "original")
            self.assertFalse((second / "caches" / "held.lock").exists())
            self.assertEqual(benchmark.digest(archive), digest)
            with self.assertRaises(FileExistsError):
                ci.restore_snapshot(archive, second)
            with self.assertRaisesRegex(ValueError, "new file outside"):
                ci.snapshot(root / "seed", root / "seed" / "inside.tar.gz")

    def test_snapshot_extraction_rejects_paths_outside_the_gradle_home(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            archive = root / "unsafe.tar.gz"
            with tarfile.open(archive, "w:gz") as out:
                entry = tarfile.TarInfo("../outside")
                entry.size = 3
                out.addfile(entry, io.BytesIO(b"bad"))
            with self.assertRaises(tarfile.TarError):
                ci.restore_snapshot(archive, root / "home")
            self.assertFalse((root / "outside").exists())

    def test_profile_parsing_keeps_task_sums_separate_from_wall_time(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "profile.html"
            path.write_text(PROFILE, encoding="utf-8")
            result = ci.profile_metrics(path)
            self.assertEqual(result["summary_ms"]["Total Build Time"], 62345)
            self.assertEqual(result["assertion_task_duration_sum_ms"], 500)
            path.write_text("<h2>Summary</h2>", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "Incomplete"):
                ci.profile_metrics(path)
        self.assertEqual(ci.duration_ms("1h 2m 3.5s"), 3723500)
        with self.assertRaisesRegex(ValueError, "Unrecognized"):
            ci.duration_ms("unknown")

    def test_build_verification_requires_live_read_only_fresh_executed_assertions(self):
        log = build_log()
        actual = {ASSERTION: RECORD}
        self.assertEqual(ci.validate_build(log, actual)[ASSERTION], "EXECUTED")
        for changed in (
            log.replace("push=false", "push=true"),
            log.replace("enabled=true", "enabled=false"),
            log.replace(ASSERTION + "\n", ASSERTION + " FROM-CACHE\n"),
            log.replace("Configuration cache entry stored.", "Reusing configuration cache"),
            log.replace("BUILD SUCCESSFUL", "BUILD FAILED"),
        ):
            with self.subTest(log=changed), self.assertRaises(ValueError):
                ci.validate_build(changed, actual)

    def test_each_revision_and_control_start_fresh_without_preparation_builds(self):
        with tempfile.TemporaryDirectory() as directory:
            runner = self.runner(Path(directory))
            original_snapshot = benchmark.digest(runner.snapshot)
            with patch.object(benchmark, "run", side_effect=self.fake_build) as run:
                runner.measure()
            commands = [call for call in run.call_args_list if call.kwargs.get("log") is not None]
            self.assertEqual(len(commands), 3)
            homes = [call.kwargs["env"]["GRADLE_USER_HOME"] for call in commands]
            self.assertEqual(len(set(homes)), 3)
            self.assertTrue(all(not Path(home).exists() for home in homes))
            self.assertEqual(benchmark.digest(runner.snapshot), original_snapshot)
            samples = json.loads((runner.reports / "samples.json").read_text())
            self.assertTrue(samples["complete"])
            self.assertEqual([sample["label"] for sample in samples["samples"]], list(ci.LABELS))
            self.assertEqual(samples["samples"][1]["revision"], "before")
            for sample in samples["samples"]:
                self.assertEqual(sample["discovery"]["metadata_documents"], 1)
                self.assertEqual(sample["discovery"]["project_count"], 1)
                self.assertTrue((runner.reports / sample["label"] / "profile").is_dir())

    def test_even_round_starts_with_candidate_and_still_uses_baseline_reference(self):
        with tempfile.TemporaryDirectory() as directory:
            runner = self.runner(Path(directory))
            runner.args.round = 2
            with patch.object(benchmark, "run", side_effect=self.fake_build):
                runner.measure()
            self.assertEqual([sample["label"] for sample in runner.samples], ["candidate", "control", "baseline"])

    def test_changed_dependency_reports_invalidate_the_pair_and_remain_available(self):
        with tempfile.TemporaryDirectory() as directory:
            runner = self.runner(Path(directory))

            def changed_build(command, **kwargs):
                result = self.fake_build(command, **kwargs)
                log = kwargs.get("log")
                if log is not None and log.parent.name == "candidate":
                    write_record(log.parent / "assertions", RECORD.replace("sha256", "different"))
                return result

            with patch.object(benchmark, "run", side_effect=changed_build):
                with self.assertRaisesRegex(ValueError, "candidate assertion/dependency workload"):
                    runner.measure()
            self.assertFalse(json.loads((runner.reports / "samples.json").read_text())["complete"])
            self.assertEqual(len(runner.samples), 3)
            for label in ci.LABELS:
                self.assertTrue((runner.reports / label / "console.log").is_file())

    def test_failed_build_is_recorded_without_retry_or_successful_report(self):
        with tempfile.TemporaryDirectory() as directory:
            runner = self.runner(Path(directory))

            def failed_build(command, **kwargs):
                log = kwargs.get("log")
                if log is not None:
                    log.write_text("BUILD FAILED", encoding="utf-8")
                    raise subprocess.CalledProcessError(1, command)
                return self.fake_build(command, **kwargs)

            with patch.object(benchmark, "run", side_effect=failed_build):
                with self.assertRaises(subprocess.CalledProcessError):
                    runner.measure()
            result = json.loads((runner.reports / "samples.json").read_text())
            self.assertFalse(result["complete"])
            self.assertEqual(len(result["samples"]), 1)
            self.assertFalse(result["samples"][0]["process_success"])
            self.assertGreater(result["samples"][0]["command_ms"], 0)
            self.assertTrue((runner.root / "baseline" / "checkout").is_dir())

    def report_fixture(self, root, round_number, partition, durations):
        directory = root / f"muzzle-ci-{round_number}-{partition}"
        directory.mkdir()
        write_identity = {
            "schema": 1, "round": round_number, "partition": partition,
            "revisions": {"baseline": "before", "candidate": "after"},
            "cache_snapshot_sha256": "same-snapshot",
        }
        ci.write_json(directory / "identity.json", write_identity)
        samples = []
        assertion = ASSERTION.replace(":example:", f":example{partition}:")
        for label in benchmark.order(round_number - 1):
            output = directory / label
            output.mkdir()
            write_record(output / "assertions", RECORD.replace(ASSERTION, assertion))
            log = build_log(assertion)
            (output / "console.log").write_text(log, encoding="utf-8")
            metadata = output / "metadata" / "instrumentation" / "example" / "build" / "muzzleRepo"
            metadata.mkdir(parents=True)
            (metadata / "maven-metadata-fixture.xml").write_text("<metadata/>", encoding="utf-8")
            profile = output / "profile"
            profile.mkdir()
            (profile / "profile-fixture.html").write_text(PROFILE, encoding="utf-8")
            samples.append({
                "label": label, "revision": "after" if label == "candidate" else "before",
                "process_success": True, "command_ms": durations[label],
                "task_outcomes": benchmark.task_outcomes(log),
                "assertions": 1, "checkout_ms": 1, "cache_restore_ms": 2,
                "profile": ci.profile_metrics(profile / "profile-fixture.html"),
                "discovery": {
                    "projects": {":instrumentation:example": ["maven-metadata-fixture.xml"]},
                    "project_count": 1, "metadata_documents": 1,
                    "metadata_sha256": benchmark.inventory(output / "metadata"),
                },
            })
        ci.write_json(directory / "samples.json", {"complete": True, "samples": samples})
        return directory

    def test_primary_metric_takes_each_round_maximum_before_summarizing(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for round_number, partition, baseline, candidate in (
                (1, 1, 100, 120), (1, 2, 90, 60), (2, 1, 10, 20), (2, 2, 100, 80),
            ):
                self.report_fixture(root, round_number, partition, {
                    "baseline": baseline, "control": baseline + 1, "candidate": candidate,
                })
            result = ci.aggregate(root, root / "summary", 2, [1, 2])
            self.assertEqual(result["maximum_slice_medians_ms"]["baseline"], 100)
            self.assertEqual(result["maximum_slice_medians_ms"]["candidate"], 100)
            self.assertEqual(result["maximum_slice_paired"]["candidate"]["delta_ms"]["median"], 0)
            self.assertEqual(result["maximum_slice_paired"]["candidate"]["delta_ms"]["min"], -20)
            self.assertEqual(result["maximum_slice_paired"]["candidate"]["delta_ms"]["max"], 20)
            self.assertIn("Functional run only", (root / "summary" / "summary.md").read_text())

    def test_report_rejects_incomplete_jobs_different_snapshots_and_cross_round_drift(self):
        for defect in ("incomplete", "snapshot", "workload"):
            with self.subTest(defect=defect), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                self.report_fixture(root, 1, 1, dict.fromkeys(ci.LABELS, 100))
                second = self.report_fixture(root, 2, 1, dict.fromkeys(ci.LABELS, 100))
                if defect == "incomplete":
                    path = second / "samples.json"
                    result = json.loads(path.read_text())
                    result["complete"] = False
                    ci.write_json(path, result)
                elif defect == "snapshot":
                    path = second / "identity.json"
                    identity = json.loads(path.read_text())
                    identity["cache_snapshot_sha256"] = "other-snapshot"
                    ci.write_json(path, identity)
                else:
                    write_record(second / "candidate" / "assertions", RECORD.replace(ASSERTION, ASSERTION.replace(":example:", ":example1:")).replace("sha256", "changed"))
                with self.assertRaises(ValueError):
                    ci.aggregate(root, root / "summary", 2, [1])
                self.assertFalse((root / "summary" / "summary.md").exists())

    def test_existing_or_overlapping_work_directory_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for path in (root, root / "inside"):
                with self.subTest(path=path), self.assertRaisesRegex(ValueError, "new and outside"):
                    ci.Comparison(SimpleNamespace(repository=root, work_dir=path))

    def test_preflight_checks_snapshot_and_refuses_remote_writer_credentials(self):
        for defect in ("snapshot", "credentials", None):
            with self.subTest(defect=defect), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                archive, digest = self.snapshot(root)
                source = root / "source"
                source.mkdir()
                args = SimpleNamespace(
                    repository=source, work_dir=root / "work",
                    cache_snapshot=archive, snapshot_sha256="wrong" if defect == "snapshot" else digest,
                    baseline="before", candidate="after", partition=1, round=1,
                )

                def git(*arguments):
                    if arguments[0] == "rev-parse":
                        return arguments[-1].removesuffix("^{commit}").encode()
                    if arguments[0] == "ls-tree":
                        return b"100644 blob abc\tinstrumentation/example/src/main/java/Example.java\0"
                    if arguments[0] == "show":
                        return b"25" if arguments[-1].endswith(":.java-version") else b"same"
                    raise AssertionError(arguments)

                java = root / "jdk" / "bin" / "java"
                with (
                    patch.object(ci.Comparison, "git", side_effect=git),
                    patch.object(ci.shutil, "which", return_value=str(java)),
                    patch.object(benchmark, "run", return_value=b"openjdk 25.0.5 fixture"),
                    patch.dict(os.environ, {"DEVELOCITY_ACCESS_KEY": "fixture" if defect == "credentials" else ""}),
                ):
                    if defect:
                        with self.assertRaisesRegex(ValueError, "SHA-256" if defect == "snapshot" else "writer credentials"):
                            ci.Comparison(args)
                    else:
                        runner = ci.Comparison(args)
                        self.assertEqual(runner.identity["cache_snapshot_sha256"], digest)
                        self.assertEqual(runner.env["JAVA_HOME"], str(java.parent.parent))

    def test_report_rechecks_captured_metadata_not_only_reported_counts(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            report = self.report_fixture(root, 1, 1, dict.fromkeys(ci.LABELS, 100))
            metadata = next((report / "candidate" / "metadata").rglob("*.xml"))
            metadata.write_text("changed", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "Captured version metadata"):
                ci.aggregate(root, root / "summary", 1, [1])


if __name__ == "__main__":
    unittest.main()
