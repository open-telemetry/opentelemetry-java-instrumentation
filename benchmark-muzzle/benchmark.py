#!/usr/bin/env python3
"""Compare direct Gradle Muzzle runs with frozen inputs and warmed checkouts."""

import argparse
import csv
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import shutil
import statistics
import subprocess
import sys
import time


CONTROLS = Path(
    "gradle-plugins/src/main/java/io/opentelemetry/javaagent/muzzle/MuzzleBenchmarkSupport.java"
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
    print(f"Running {log}", flush=True)
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


def fixture_identity(tree):
    sources = [
        entry for entry in tree.split(b"\0")
        if entry and b"/src/main" in entry.split(b"\t", 1)[1]
    ]
    if not sources:
        raise ValueError("No instrumentation main sources found")
    return hashlib.sha256(b"\0".join(sorted(sources))).hexdigest()


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


def task_outcomes(text):
    tasks = {}
    for task, outcome in re.findall(
        r"^> Task (\S+)(?: (UP-TO-DATE|FROM-CACHE|NO-SOURCE|SKIPPED|FAILED))?\s*$",
        text, re.MULTILINE,
    ):
        tasks[task] = outcome or "EXECUTED"
    if not tasks:
        raise ValueError("No Gradle task outcomes in build log")
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
    def __init__(self, args, *, dependency_offline=True):
        self.started = time.perf_counter()
        self.args = args
        self.dependency_offline = dependency_offline
        self.source = args.repository.resolve()
        self.root = args.work_dir.resolve()
        if (
            self.root.exists() or self.root == self.source
            or self.root in self.source.parents or self.source in self.root.parents
        ):
            raise ValueError("--work-dir must be a new directory outside the source checkout")
        self.root.mkdir(parents=True)
        self.checkouts = {
            label: self.root / label for label in ("baseline", "candidate")
        }
        self.home = self.root / "gradle-home"
        self.repository = self.root / "metadata"
        self.audit = self.root / "audit"
        self.reports = self.root / "reports"
        self.reports.mkdir()
        self.phases = []
        self.preparation_ms = 0
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
        fixtures = {
            label: fixture_identity(self.git("ls-tree", "-rz", ref, "--", "instrumentation"))
            for label, ref in self.revisions.items()
        }
        require_equal(fixtures["baseline"], fixtures["candidate"], "Instrumentation fixture sources")
        java_version = run([str(java), "--version"], env=self.env).decode("utf-8")
        required_java = self.git("show", f"{self.revisions['baseline']}:.java-version").decode().strip()
        actual_java = re.search(r"\b(?:openjdk|java) (\d+)", java_version)
        if actual_java is None or actual_java[1] != required_java.split(".")[0]:
            raise ValueError(f"PATH must select the repository's required JDK {required_java}")
        self.input_identity = dict(
            self.workload, partition=args.partition, runtime=runtime,
            instrumentation_sources_sha256=fixtures["baseline"],
        )
        if not dependency_offline:
            self.input_identity["gradle_dependency_resolution"] = "online"
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
                "workload": self.input_identity,
                "measurement": "direct Gradle command wall time",
                "platform": platform.platform(),
                "cpu_count": os.cpu_count(),
                "cpu": json.loads(run(["lscpu", "--json"])),
                "runner_image": os.environ.get("ImageVersion"),
                "workflow_run": os.environ.get("GITHUB_RUN_ID"),
                "max_workers": 4,
                "remote_build_cache": False,
                "gradle_dependency_resolution": "offline" if dependency_offline else "online",
                "live_metadata_pair": getattr(args, "live_metadata_pair", False),
                "java": java_version,
                "cache_conditions": "cold daemon, fresh configuration cache, warm workspace and dependencies",
            }, indent=2) + "\n", encoding="utf-8",
        )
        self.setup_ms = (time.perf_counter() - self.started) * 1000

    def git(self, *arguments):
        return run(["git", "-C", str(self.source), *arguments])

    def revision(self, ref):
        return self.git(
            "rev-parse", "--verify", "--end-of-options", f"{ref}^{{commit}}"
        ).decode().strip()

    def remove(self, path):
        allowed = path == self.audit or path in {
            checkout / ".gradle" / "configuration-cache"
            for checkout in self.checkouts.values()
        }
        if not allowed or path.is_symlink():
            raise ValueError(f"Refusing to remove unmanaged path {path}")
        if path.exists():
            shutil.rmtree(path)

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

    def build(self, label, name, *, offline, preparation=False):
        if offline and self.frozen_metadata is None:
            raise ValueError("Offline builds require a captured metadata snapshot")
        checkout = self.checkouts[label]
        destination = self.reports / name
        destination.mkdir(parents=True)
        self.remove(self.audit)
        self.remove(checkout / ".gradle" / "configuration-cache")
        command = [
            str(checkout / "gradlew"), self.task, "--no-daemon", "--configuration-cache",
            *self.properties(offline=self.frozen_metadata is not None),
        ]
        if offline and self.dependency_offline:
            command.append("--offline")
        started = time.perf_counter()
        success = False
        try:
            run(command, cwd=checkout, env=self.env, log=destination / "console.log")
            success = True
        finally:
            elapsed_ms = (time.perf_counter() - started) * 1000
            self.phases.append({
                "name": name, "label": label, "elapsed_ms": elapsed_ms,
                "process_success": success,
            })
            (self.reports / "phases.json").write_text(
                json.dumps(self.phases, indent=2) + "\n", encoding="utf-8",
            )
            print(f"{name}: {elapsed_ms / 1000:.2f}s, process_success={success}", flush=True)
        shutil.copytree(self.audit, destination / "assertions")
        text = (destination / "console.log").read_text(encoding="utf-8", errors="replace")
        if "Reusing configuration cache" in text:
            raise ValueError("Fresh-configuration scenario reused the configuration cache")
        if "Configuration cache entry stored" not in text:
            raise ValueError("Missing confirmation of fresh configuration cache storage")
        actual = manifests(self.audit)
        if offline:
            if self.expected is None:
                self.expected = actual
            require_equal(self.expected, actual, "Assertion/dependency workload")
        if self.frozen_metadata is not None:
            require_equal(self.frozen_metadata, inventory(self.repository), "Frozen version metadata")
        outcomes = task_outcomes(text)
        self.validate_outcomes(actual, outcomes, preparation=preparation)
        return {"total execution time": elapsed_ms}, outcomes

    def validate_outcomes(self, actual, outcomes, *, preparation):
        if not preparation:
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

    def prepare(self):
        control_hash = None
        for label in ("baseline", "candidate"):
            checkout = self.checkouts[label]
            run(["git", "clone", "--shared", "--quiet", "--no-checkout",
                 str(self.source), str(checkout)])
            run(["git", "-C", str(checkout), "checkout", "--quiet", "--detach",
                 self.revisions[label]])
            if not (checkout / CONTROLS).exists():
                raise ValueError(
                    f"{label} revision lacks benchmark support; "
                    "both revisions must contain the benchmark commit"
                )
            if control_hash is None:
                control_hash = digest(checkout / CONTROLS)
            require_equal(control_hash, digest(checkout / CONTROLS), "Benchmark controls")
        for label in ("baseline", "candidate"):
            online = getattr(self.args, "online_dependencies", False)
            self.build(
                label, f"bootstrap-{label}",
                offline=online and self.frozen_metadata is not None, preparation=True,
            )
            if self.frozen_metadata is None:
                self.frozen_metadata = inventory(self.repository)
                if not self.frozen_metadata:
                    raise ValueError("No Aether version metadata captured")
                if online:
                    self.expected = manifests(self.audit)
        if not getattr(self.args, "online_dependencies", False):
            for label in ("baseline", "candidate"):
                self.build(label, f"warmup-{label}", offline=True, preparation=True)
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
        started = time.perf_counter()
        try:
            self.prepare()
        finally:
            self.preparation_ms = (time.perf_counter() - started) * 1000
        self.write_results()
        for round_number in range(self.args.rounds):
            for label in order(round_number):
                source = "baseline" if label == "control" else label
                metrics, outcomes = self.build(
                    source, f"round-{round_number + 1}-{label}", offline=True,
                )
                self.samples.append({
                    "round": round_number + 1, "label": label,
                    "revision": self.revisions[source], "metrics": metrics,
                    "task_outcomes": outcomes,
                })
                self.write_results()
        if getattr(self.args, "live_metadata_pair", False):
            self.measure_live_metadata()
        self.write_results(complete=True)

    def measure_live_metadata(self):
        repository, frozen = self.repository, self.frozen_metadata
        self.live_metadata_samples = []
        try:
            for label in ("baseline", "candidate"):
                self.repository = self.root / f"live-metadata-{label}"
                self.frozen_metadata = None
                metrics, outcomes = self.build(
                    label, f"live-metadata-{label}", offline=False,
                )
                self.live_metadata_samples.append({
                    "label": label, "metrics": metrics, "task_outcomes": outcomes,
                    "metadata": inventory(self.repository),
                })
                (self.reports / "live-metadata-samples.json").write_text(
                    json.dumps(self.live_metadata_samples, indent=2) + "\n", encoding="utf-8",
                )
                require_equal(self.expected, manifests(self.audit), "Live assertion/dependency workload")
        finally:
            self.repository, self.frozen_metadata = repository, frozen

    def write_results(self, *, complete=False):
        (self.reports / "samples.json").write_text(
            json.dumps({"complete": complete, "samples": self.samples}, indent=2) + "\n",
            encoding="utf-8",
        )
        total_ms = (time.perf_counter() - self.started) * 1000
        measured_ms = sum(
            phase["elapsed_ms"] for phase in self.phases
            if phase["name"].startswith(("round-", "live-metadata-"))
        )
        timing = {
            "setup_ms": self.setup_ms,
            "preparation_ms": self.preparation_ms,
            "measured_gradle_ms": measured_ms,
            "overhead_ms": total_ms - self.setup_ms - self.preparation_ms - measured_ms,
            "total_ms": total_ms,
        }
        (self.reports / "timing.json").write_text(
            json.dumps(timing, indent=2) + "\n", encoding="utf-8",
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
            writer.writerow(["round", "label", "revision", "total_ms"])
            for sample in self.samples:
                writer.writerow([
                    sample["round"], sample["label"], sample["revision"],
                    sample["metrics"]["total execution time"],
                ])
        baseline = stats["baseline"]["total execution time"]["median_ms"]
        candidate = stats["candidate"]["total execution time"]["median_ms"]
        control = stats["control"]["total execution time"]["median_ms"]
        unresolved = sum(
            any(line.startswith("unresolved\t") for line in text.splitlines())
            for text in self.expected.values()
        )
        lines = [
            f"# Muzzle partition {self.args.partition}",
            "",
            "Cold daemon, fresh configuration cache, warm workspace and dependencies. "
            "Aether version metadata is frozen and offline. "
            f"Gradle dependency resolution is {'offline' if self.dependency_offline else 'online'}. "
            "Timings include workload auditing.",
            "The repeated rounds do not measure live version-metadata downloads or establish "
            "fresh-checkout CI savings.",
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
            f"Assertions with unresolved dependencies: {unresolved}. "
            "Each resolution failure is retained in the assertion records.",
            "",
            "## Run time",
            "",
            "| Phase | Time |",
            "| --- | ---: |",
            *[
                f"| {phase.removesuffix('_ms').replace('_', ' ')} | {duration / 1000:.2f}s |"
                for phase, duration in timing.items()
            ],
            "",
            "Each Gradle command is timed directly. Setup, preparation, and runner overhead "
            "are separate from the measured builds. Individual command durations are in phases.json.",
            "",
            "No samples were retried or removed. Median differences alone do not establish a speedup. "
            "Compare the paired samples and the A/A control, and repeat noisy comparisons.",
        ]
        if getattr(self, "live_metadata_samples", None):
            lines += [
                "",
                "## Cold live-metadata probe",
                "",
                "One baseline/candidate pair uses separate empty Aether repositories and live Maven "
                "version discovery. Commands include configuration, network requests, and assertions. "
                "All assertion/dependency reports must match the independently prepared frozen workload. "
                "A single pair is diagnostic, not a repeatable performance conclusion.",
                "",
                "| Revision | Command time | Metadata files |",
                "| --- | ---: | ---: |",
                *[
                    f"| {sample['label']} | {sample['metrics']['total execution time'] / 1000:.2f}s "
                    f"| {len(sample['metadata'])} |"
                    for sample in self.live_metadata_samples
                ],
            ]
        if self.args.rounds < 5:
            lines += ["", "**Functional run only. Fewer than five rounds cannot support a performance claim.**"]
        (self.reports / "summary.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--baseline", required=True)
    parser.add_argument("--candidate", required=True)
    parser.add_argument("--work-dir", type=Path, required=True)
    parser.add_argument("--partition", type=int, choices=range(1, 5), required=True)
    parser.add_argument("--rounds", type=int, choices=range(1, 11), default=5)
    parser.add_argument("--inputs", type=Path)
    parser.add_argument("--seed-gradle-home", type=Path)
    parser.add_argument(
        "--online-dependencies", action="store_true",
        help="Keep Gradle resolution online and combine dependency preparation with reference generation",
    )
    parser.add_argument(
        "--live-metadata-pair", action="store_true",
        help="Add one cold live-Maven baseline/candidate probe after the frozen repeated rounds",
    )
    args = parser.parse_args()
    if args.live_metadata_pair and not args.online_dependencies:
        parser.error("--live-metadata-pair requires --online-dependencies")
    if sys.platform != "linux":
        parser.error("Build measurements run on Linux; runner unit tests also support Windows")
    benchmark = Benchmark(args, dependency_offline=not args.online_dependencies)
    try:
        benchmark.measure()
    except (OSError, ValueError, subprocess.CalledProcessError) as error:
        (benchmark.reports / "failure.txt").write_text(str(error) + "\n", encoding="utf-8")
        benchmark.write_results()
        raise


if __name__ == "__main__":
    main()
