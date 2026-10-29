#!/usr/bin/env python3
"""Compare real Muzzle partitions with frozen inputs and restored build state."""

import argparse
import csv
import hashlib
import json
import math
import os
from pathlib import Path
import platform
import re
import shutil
import statistics
import subprocess
import sys


PROFILER_VERSION = "0.26.0"
PROFILER_SHA256 = "c573cdaffac8ecfa60cdd0624274511f46cbdde77fbba39ce35eae0fae7c9877"
PLUGIN = Path(
    "gradle-plugins/src/main/kotlin/"
    "io.opentelemetry.instrumentation.muzzle-check.gradle.kts"
)
CONTROLS = Path(
    "gradle-plugins/src/main/java/io/opentelemetry/javaagent/muzzle/MuzzleBenchmark.java"
)
RUNTIME_FILES = (
    ".java-version",
    "gradle.properties",
    "gradle/wrapper/gradle-wrapper.properties",
    ".github/config/latest-dep-versions.json",
)


def run(command, *, cwd=None, env=None, log=None, input=None):
    kwargs = {"creationflags": subprocess.CREATE_NO_WINDOW} if os.name == "nt" else {}
    if log is None:
        return subprocess.run(
            command, cwd=cwd, env=env, input=input, check=True,
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, **kwargs,
        ).stdout
    print(f"Running {log.name}", flush=True)
    with log.open("wb") as output:
        subprocess.run(
            command, cwd=cwd, env=env, input=input, check=True,
            stdout=output, stderr=subprocess.STDOUT, **kwargs,
        )
    return b""


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def inventory(directory):
    return {
        path.relative_to(directory).as_posix(): digest(path)
        for path in sorted(directory.rglob("*")) if path.is_file()
    }


def manifests(directory):
    result = {}
    for path in sorted(directory.glob("*.txt")):
        text = path.read_text(encoding="utf-8")
        task = text.splitlines()[0]
        if ":muzzle-Assert" not in task or task in result:
            raise ValueError(f"Invalid or duplicate assertion manifest: {path}")
        result[task] = text
    if not result:
        raise ValueError(f"No successful Muzzle assertions recorded in {directory}")
    return result


def require_equal(expected, actual, label):
    if expected != actual:
        if isinstance(expected, dict) and isinstance(actual, dict):
            changed = sorted(
                key for key in expected.keys() | actual.keys()
                if expected.get(key) != actual.get(key)
            )
            raise ValueError(f"{label} mismatch: {changed[:10]}")
        raise ValueError(f"{label} mismatch")


def measurements(path):
    with path.open(encoding="utf-8", newline="") as stream:
        rows = [row for row in csv.DictReader(stream) if row["Phase"] == "MEASURE"]
    values = {}
    for row in rows:
        if row["Iteration"] != "1" or row["Sample"] in values:
            raise ValueError(f"Expected exactly one measured invocation in {path}")
        value = float(row["Duration"])
        if not math.isfinite(value) or value < 0:
            raise ValueError(f"Invalid duration in {path}")
        values[row["Sample"]] = value
    if values.get("total execution time", 0) <= 0 or values.get("task start", 0) <= 0:
        raise ValueError(f"Missing wall-clock or task-start measurement in {path}")
    return values


def task_outcomes(text):
    tasks = {}
    for task, outcome in re.findall(
        r"^> Task (\S+)(?: (UP-TO-DATE|FROM-CACHE|NO-SOURCE|SKIPPED|FAILED))?\s*$",
        text, re.MULTILINE,
    ):
        tasks[task] = outcome or "EXECUTED"
    if not tasks:
        raise ValueError("No Gradle task outcomes in profiler log")
    return tasks


def order(round_number):
    return (
        ("baseline", "control", "candidate") if round_number % 2 == 0
        else ("candidate", "control", "baseline")
    )


def distribution(values):
    return {
        "median_ms": statistics.median(values),
        "min_ms": min(values),
        "max_ms": max(values),
        "samples": len(values),
        "stdev_ms": statistics.stdev(values) if len(values) > 1 else 0,
    }


def summary(samples):
    result = {}
    for label in ("baseline", "control", "candidate"):
        matching = [sample["metrics"] for sample in samples if sample["label"] == label]
        result[label] = {
            metric: distribution([sample[metric] for sample in matching])
            for metric in matching[0]
        }
    return result


class Benchmark:
    def __init__(self, args):
        self.args = args
        self.source = args.repository.resolve()
        self.root = args.work_dir.resolve()
        if (
            self.root.exists() or self.root == self.source
            or self.root in self.source.parents or self.source in self.root.parents
        ):
            raise ValueError("--work-dir must be a new directory outside the source checkout")
        self.root.mkdir(parents=True)
        self.checkout = self.root / "checkout"
        self.home = self.root / "gradle-home"
        self.repository = self.root / "metadata"
        self.audit = self.root / "audit"
        self.profiler_output = self.root / "profiler-output"
        self.reports = self.root / "reports"
        self.snapshots = self.root / "snapshots"
        self.reports.mkdir()
        self.snapshots.mkdir()
        self.init_script = self.root / "benchmark.init.gradle"
        self.init_script.write_text(
            "settingsEvaluated { settings ->\n"
            "  def remote = settings.buildCache.remote\n"
            "  if (remote != null) { remote.enabled = false }\n"
            "}\n", encoding="utf-8",
        )
        java = shutil.which("java")
        if java is None:
            raise ValueError("java is missing from PATH")
        java = Path(java).resolve()
        self.env = dict(
            os.environ, GRADLE_USER_HOME=str(self.home),
            JAVA_HOME=str(java.parent.parent),
        )
        if args.seed_gradle_home:
            for name in ("caches", "wrapper", "jdks"):
                source = args.seed_gradle_home / name
                if source.is_dir():
                    shutil.copytree(
                        source, self.home / name, symlinks=True,
                        ignore=shutil.ignore_patterns("*.lock"),
                    )
        self.workload = json.loads(
            Path(__file__).with_name("workload.json").read_text(encoding="utf-8")
        )
        self.task = f":instrumentation:muzzle{args.partition}"
        self.expected = None
        self.frozen_metadata = None
        self.samples = []
        self.support = []
        for ref in args.support_commit:
            commit = self.revision(ref)
            support = self.git(
                "diff", f"{commit}^", commit, "--", "gradle-plugins/src/main",
            )
            if not support:
                raise ValueError(f"Support commit {commit} contains no benchmark plugin changes")
            self.support.append(support)
        self.revisions = {
            "baseline": self.revision(args.baseline),
            "candidate": self.revision(args.candidate),
        }
        runtime = {
            path: hashlib.sha256(self.git("show", f"{self.revisions['baseline']}:{path}")).hexdigest()
            for path in RUNTIME_FILES
        }
        for path in RUNTIME_FILES:
            require_equal(
                runtime[path],
                hashlib.sha256(self.git("show", f"{self.revisions['candidate']}:{path}")).hexdigest(),
                path,
            )
        java_version = run([str(java), "--version"], env=self.env).decode("utf-8")
        required_java = self.git("show", f"{self.revisions['baseline']}:.java-version").decode().strip()
        actual_java = re.search(r"\b(?:openjdk|java) (\d+)", java_version)
        if actual_java is None or actual_java[1] != required_java.split(".")[0]:
            raise ValueError(f"PATH must select the repository's required JDK {required_java}")
        self.input_identity = dict(
            self.workload, partition=args.partition, runtime=runtime,
        )
        if args.inputs:
            saved = json.loads((args.inputs / "identity.json").read_text(encoding="utf-8"))
            require_equal(self.input_identity, saved["workload"], "Saved workload identity")
            require_equal(saved["metadata"], inventory(args.inputs / "metadata"), "Saved metadata")
            shutil.copytree(args.inputs / "metadata", self.repository)
            self.frozen_metadata = saved["metadata"]
            self.expected = manifests(args.inputs / "assertions")
        (self.reports / "identity.json").write_text(
            json.dumps({
                "revisions": self.revisions,
                "harness_revision": self.revision("HEAD"),
                "support_patch_sha256": hashlib.sha256(b"".join(self.support)).hexdigest(),
                "workload": self.input_identity,
                "profiler_version": PROFILER_VERSION,
                "profiler_sha256": PROFILER_SHA256,
                "platform": platform.platform(),
                "cpu_count": os.cpu_count(),
                "cpu": json.loads(run(["lscpu", "--json"])),
                "runner_image": os.environ.get("ImageVersion"),
                "workflow_run": os.environ.get("GITHUB_RUN_ID"),
                "max_workers": 4,
                "remote_build_cache": False,
                "java": java_version,
                "cache_conditions": "cold daemon, fresh configuration cache, warm workspace and dependencies",
            }, indent=2) + "\n", encoding="utf-8",
        )

    def git(self, *arguments):
        return run(["git", "-C", str(self.source), *arguments])

    def revision(self, ref):
        return self.git(
            "rev-parse", "--verify", "--end-of-options", f"{ref}^{{commit}}"
        ).decode().strip()

    def remove(self, path):
        allowed = path.parent == self.root and path.name in {
            "checkout", "gradle-home", "audit", "profiler-output",
        }
        allowed |= path.parent == self.snapshots and path.name in {
            "baseline", "candidate", "gradle-home",
        }
        if not allowed or path.is_symlink():
            raise ValueError(f"Refusing to remove unmanaged path {path}")
        if path.exists():
            shutil.rmtree(path)

    def copy(self, source, target):
        self.remove(target)
        shutil.copytree(source, target, symlinks=True)

    def properties(self, *, offline):
        return [
            f"-PmuzzleBenchmarkSeed={self.workload['seed']}",
            f"-PmuzzleBenchmarkRepository={self.repository}",
            f"-PmuzzleBenchmarkRepositoryOffline={str(offline).lower()}",
            f"-PmuzzleBenchmarkReportDirectory={self.audit}",
            f"-Dotel.javaagent.muzzle.versions.limit={self.workload['versions_limit']}",
            "--no-scan", "--console=plain",
            "--max-workers=4",
            "--init-script", str(self.init_script),
        ]

    def gradle(self, label, *, offline):
        run(
            [str(self.checkout / "gradlew"), self.task, "--no-daemon",
             *self.properties(offline=offline)],
            cwd=self.checkout, env=self.env, log=self.reports / f"prepare-{label}.log",
        )
        self.stop_daemons()

    def stop_daemons(self):
        run(
            [str(self.checkout / "gradlew"), "--stop", "--console=plain"],
            cwd=self.checkout, env=self.env,
        )

    def reset_configuration(self):
        configuration = self.checkout / ".gradle" / "configuration-cache"
        if configuration.exists():
            shutil.rmtree(configuration)

    def invoke_profiler(self, destination, *, profile=False, preparation=False):
        self.remove(self.audit)
        self.remove(self.profiler_output)
        self.reset_configuration()
        scenario = self.root / "muzzle.scenarios"
        scenario.write_text(
            "muzzle {\n"
            f"  tasks = {json.dumps([self.task])}\n"
            f"  gradle-args = {json.dumps(['--offline', '--configuration-cache', *self.properties(offline=True)])}\n"
            "}\n", encoding="utf-8",
        )
        command = [
            str(self.args.profiler.resolve()), "--benchmark", "--single-shot", "--no-daemon",
            "--measure-config-time", "--csv-format", "long",
            "--project-dir", str(self.checkout), "--gradle-user-home", str(self.home),
            "--scenario-file", str(scenario), "--output-dir", str(self.profiler_output),
        ]
        if profile:
            command.extend(["--profile", "jfr"])
        command.append("muzzle")
        destination.mkdir(parents=True)
        run(command, cwd=self.checkout, env=self.env, log=destination / "console.log")
        self.stop_daemons()
        shutil.copytree(self.profiler_output, destination / "profiler")
        shutil.copytree(self.audit, destination / "assertions")
        text = (destination / "console.log").read_text(encoding="utf-8", errors="replace")
        logs = [path for path in self.profiler_output.rglob("*") if path.suffix in {".log", ".txt"}]
        for log in logs:
            text += "\n" + log.read_text(encoding="utf-8", errors="replace")
        if "Reusing configuration cache" in text:
            raise ValueError("Fresh-configuration scenario reused the configuration cache")
        if "Configuration cache entry stored" not in text:
            raise ValueError("Missing confirmation of fresh configuration cache storage")
        actual = manifests(self.audit)
        if self.expected is None:
            self.expected = actual
        require_equal(self.expected, actual, "Assertion/dependency workload")
        require_equal(self.frozen_metadata, inventory(self.repository), "Frozen version metadata")
        outcomes = task_outcomes(text)
        if not preparation and not profile:
            cold_compilations = [
                task for task, outcome in outcomes.items()
                if re.fullmatch(r"compile.*(?:Java|Kotlin)", task.rsplit(":", 1)[-1])
                and outcome not in {"UP-TO-DATE", "NO-SOURCE", "SKIPPED"}
            ]
            if cold_compilations:
                raise ValueError(f"Prepared compilation outputs were not reused: {cold_compilations[:10]}")
        assertion_tasks = {
            task for task, outcome in outcomes.items()
            if ":muzzle-Assert" in task and outcome == "EXECUTED"
        }
        require_equal(set(actual), assertion_tasks, "Executed assertions")
        return measurements(self.profiler_output / "benchmark.csv"), outcomes

    def prepare(self):
        control_hash = None
        for label in ("baseline", "candidate"):
            self.remove(self.checkout)
            run(["git", "clone", "--shared", "--quiet", "--no-checkout",
                 str(self.source), str(self.checkout)])
            run(["git", "-C", str(self.checkout), "checkout", "--quiet", "--detach",
                 self.revisions[label]])
            if not (self.checkout / CONTROLS).exists():
                if not self.support:
                    raise ValueError("Revision lacks benchmark support; pass --support-commit")
                for support in self.support:
                    run(["git", "-C", str(self.checkout), "apply", "--3way", "-"], input=support)
            if control_hash is None:
                control_hash = digest(self.checkout / CONTROLS)
            require_equal(control_hash, digest(self.checkout / CONTROLS), "Benchmark controls")
            self.remove(self.audit)
            self.gradle(label, offline=self.frozen_metadata is not None)
            if self.frozen_metadata is None:
                self.frozen_metadata = inventory(self.repository)
                if not self.frozen_metadata:
                    raise ValueError("No Aether version metadata captured")
            self.reset_configuration()
            self.copy(self.checkout, self.snapshots / label)
        for label in ("baseline", "candidate"):
            self.copy(self.snapshots / label, self.checkout)
            self.invoke_profiler(self.reports / "preparation" / label, preparation=True)
            self.reset_configuration()
            self.copy(self.checkout, self.snapshots / label)
        self.copy(self.home, self.snapshots / "gradle-home")
        inputs = self.reports / "inputs"
        inputs.mkdir()
        shutil.copytree(self.repository, inputs / "metadata")
        shutil.copytree(self.audit, inputs / "assertions")
        (inputs / "identity.json").write_text(
            json.dumps({
                "workload": self.input_identity, "metadata": self.frozen_metadata,
            }, indent=2) + "\n", encoding="utf-8",
        )

    def measure(self):
        self.prepare()
        for round_number in range(self.args.rounds):
            for label in order(round_number):
                source = "baseline" if label == "control" else label
                self.copy(self.snapshots / source, self.checkout)
                self.copy(self.snapshots / "gradle-home", self.home)
                metrics, outcomes = self.invoke_profiler(
                    self.reports / f"round-{round_number + 1}-{label}"
                )
                self.samples.append({
                    "round": round_number + 1, "label": label,
                    "revision": self.revisions[source], "metrics": metrics,
                    "task_outcomes": outcomes,
                })
                self.write_results()
        if self.args.profile:
            for label in ("baseline", "candidate"):
                self.copy(self.snapshots / label, self.checkout)
                self.copy(self.snapshots / "gradle-home", self.home)
                self.invoke_profiler(self.reports / "profiles" / label, profile=True)
        self.write_results(complete=True)

    def write_results(self, *, complete=False):
        (self.reports / "samples.json").write_text(
            json.dumps({"complete": complete, "samples": self.samples}, indent=2) + "\n",
            encoding="utf-8",
        )
        if not complete:
            return
        stats = summary(self.samples)
        pairs = {}
        for sample in self.samples:
            pairs.setdefault(sample["round"], {})[sample["label"]] = sample["metrics"]
        stats["paired_changes"] = {
            label: {
                metric: distribution([
                    pair[label][metric] - pair["baseline"][metric]
                    for pair in pairs.values()
                ])
                for metric in pairs[1]["baseline"]
            }
            for label in ("control", "candidate")
        }
        (self.reports / "statistics.json").write_text(
            json.dumps(stats, indent=2) + "\n", encoding="utf-8",
        )
        with (self.reports / "samples.csv").open("w", encoding="utf-8", newline="") as stream:
            writer = csv.writer(stream)
            writer.writerow(["round", "label", "revision", "total_ms", "task_start_ms"])
            for sample in self.samples:
                writer.writerow([
                    sample["round"], sample["label"], sample["revision"],
                    sample["metrics"]["total execution time"],
                    sample["metrics"]["task start"],
                ])
        baseline = stats["baseline"]["total execution time"]["median_ms"]
        candidate = stats["candidate"]["total execution time"]["median_ms"]
        control = stats["control"]["total execution time"]["median_ms"]
        lines = [
            f"# Muzzle partition {self.args.partition}",
            "",
            "Cold daemon, fresh configuration cache, warm workspace and dependencies. "
            "External dependency resolution is offline. Timings include workload auditing.",
            "",
            "| Revision | Samples | Median | Min | Max |",
            "| --- | ---: | ---: | ---: | ---: |",
        ]
        for label in ("baseline", "control", "candidate"):
            values = stats[label]["total execution time"]
            lines.append(
                f"| {label} | {values['samples']} | {values['median_ms'] / 1000:.2f}s "
                f"| {values['min_ms'] / 1000:.2f}s | {values['max_ms'] / 1000:.2f}s |"
            )
        lines += [
            "",
            f"Candidate median change: {(candidate / baseline - 1) * 100:+.2f}%. "
            f"Identical-revision control median change: {(control / baseline - 1) * 100:+.2f}%.",
            "",
            f"Verified {len(self.expected)} identical successful assertions, "
            "including resolved artifact hashes and unresolved dependency outcomes.",
            "",
            "Task-start measurements and task-cache outcomes are in the raw reports. "
            "Task start includes more than version enumeration and is not a pure Muzzle configuration metric.",
            "",
            "No samples were retried or removed. Median differences alone do not establish a speedup. "
            "Compare the paired samples and the A/A control, and repeat noisy comparisons.",
        ]
        if self.args.rounds < 5:
            lines += ["", "**Functional run only. Fewer than five rounds cannot support a performance claim.**"]
        (self.reports / "summary.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--baseline", required=True)
    parser.add_argument("--candidate", required=True)
    parser.add_argument("--support-commit", action="append", default=[])
    parser.add_argument("--profiler", type=Path, required=True)
    parser.add_argument("--work-dir", type=Path, required=True)
    parser.add_argument("--partition", type=int, choices=range(1, 5), required=True)
    parser.add_argument("--rounds", type=int, choices=range(1, 11), default=5)
    parser.add_argument("--inputs", type=Path)
    parser.add_argument("--seed-gradle-home", type=Path)
    parser.add_argument("--profile", action="store_true")
    args = parser.parse_args()
    if sys.platform != "linux":
        parser.error("Build measurements run on Linux; runner unit tests also support Windows")
    benchmark = Benchmark(args)
    try:
        benchmark.measure()
    except (OSError, ValueError, subprocess.CalledProcessError) as error:
        (benchmark.reports / "failure.txt").write_text(str(error) + "\n", encoding="utf-8")
        raise


if __name__ == "__main__":
    main()
