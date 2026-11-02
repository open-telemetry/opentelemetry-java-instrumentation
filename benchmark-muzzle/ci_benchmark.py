#!/usr/bin/env python3
"""Compare fresh-checkout Muzzle commands with live metadata and isolated caches."""

import argparse
from collections import Counter
from concurrent.futures import ThreadPoolExecutor
import hashlib
from html.parser import HTMLParser
import json
import os
from pathlib import Path
import platform
import re
import shutil
import subprocess
import sys
import tarfile
import threading
import time

import benchmark
import operation_profile


SNAPSHOT_PATHS = ("caches", "wrapper", "jdks", "gradle.properties")
LABELS = ("baseline", "control", "candidate")
INIT_SCRIPT = Path(__file__).with_name("ci.init.gradle")


def write_json(path, value):
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def sample_disk_usage(path, stop):
    initial = shutil.disk_usage(path).free
    minimum = initial
    while not stop.wait(1):
        minimum = min(minimum, shutil.disk_usage(path).free)
    final = shutil.disk_usage(path).free
    return {
        "initial_free_bytes": initial, "minimum_free_bytes": min(minimum, final),
        "final_free_bytes": final,
    }


def check_snapshot_size(path, max_bytes):
    if max_bytes <= 0:
        raise ValueError("The snapshot budget must be positive")
    size = path.stat().st_size
    if size > max_bytes:
        raise ValueError(f"Snapshot requires {size} bytes, exceeding the {max_bytes}-byte cache budget")


def snapshot(home, destination, *, max_bytes=None):
    if destination.exists() or destination.resolve().is_relative_to(home.resolve()):
        raise ValueError("The snapshot must be a new file outside the Gradle home")
    if not (home / "caches").is_dir():
        raise ValueError("The restored Gradle home has no dependency/task caches")
    destination.parent.mkdir(parents=True, exist_ok=True)

    def exclude_locks(info):
        return None if info.name.endswith(".lock") else info

    with tarfile.open(destination, "w:gz", compresslevel=1) as archive:
        for name in SNAPSHOT_PATHS:
            if (home / name).exists():
                archive.add(home / name, arcname=name, filter=exclude_locks)
    if max_bytes is not None:
        check_snapshot_size(destination, max_bytes)
    return benchmark.digest(destination)


def restore_snapshot(source, destination):
    destination.mkdir()
    with tarfile.open(source, "r:gz") as archive:
        archive.extractall(destination, filter="data")


class ProfileParser(HTMLParser):
    def __init__(self):
        super().__init__()
        self.section = None
        self.heading = None
        self.row = None
        self.cell = None
        self.tables = {}

    def handle_starttag(self, tag, attrs):
        if tag == "h2":
            self.heading = []
        elif tag == "tr":
            self.row = []
        elif tag == "td":
            self.cell = []

    def handle_data(self, data):
        if self.heading is not None:
            self.heading.append(data)
        if self.cell is not None:
            self.cell.append(data)

    def handle_endtag(self, tag):
        if tag == "h2" and self.heading is not None:
            self.section = "".join(self.heading).strip()
            self.heading = None
        elif tag == "td" and self.cell is not None:
            self.row.append("".join(self.cell).strip())
            self.cell = None
        elif tag == "tr" and self.row:
            self.tables.setdefault(self.section, []).append(self.row)
            self.row = None


def duration_ms(value):
    match = re.fullmatch(r"(?:(\d+)h)?(?:(\d+)m)?(\d+(?:\.\d+)?)s", value.replace(" ", ""))
    if match is None:
        raise ValueError(f"Unrecognized Gradle profile duration: {value}")
    hours, minutes, seconds = match.groups()
    return (int(hours or 0) * 3600 + int(minutes or 0) * 60 + float(seconds)) * 1000


def profile_metrics(path):
    parser = ProfileParser()
    parser.feed(path.read_text(encoding="utf-8"))
    summary = {row[0]: duration_ms(row[1]) for row in parser.tables.get("Summary", [])}
    if not {"Total Build Time", "Configuring Projects", "Task Execution"} <= summary.keys():
        raise ValueError("Incomplete Gradle profile summary")
    assertions = [
        row for row in parser.tables.get("Task Execution", [])
        if ":muzzle-Assert" in row[0]
    ]
    if not assertions:
        raise ValueError("Gradle profile contains no Muzzle assertions")
    return {
        "summary_ms": summary,
        "assertion_task_count": len(assertions),
        "assertion_task_duration_sum_ms": sum(duration_ms(row[1]) for row in assertions),
    }


def discovery(checkout, destination):
    projects = {}
    metadata = destination / "metadata"
    for repository in sorted(checkout.rglob("muzzleRepo")):
        if not repository.is_dir() or repository.parent.name != "build":
            continue
        relative = repository.relative_to(checkout)
        documents = sorted(repository.rglob("maven-metadata-*.xml"))
        if not documents:
            continue
        project = ":" + ":".join(relative.parts[:-2])
        projects[project] = [path.relative_to(repository).as_posix() for path in documents]
        shutil.copytree(repository, metadata / relative)
    if not projects:
        raise ValueError("No live Aether version metadata was captured")
    return {
        "projects": projects,
        "project_count": len(projects),
        "metadata_documents": sum(len(paths) for paths in projects.values()),
        "metadata_sha256": benchmark.inventory(metadata),
    }


def validate_build(log, actual, *, configuration_cache=True, connection_ttl_ms=None):
    if "BUILD SUCCESSFUL" not in log:
        raise ValueError("Missing successful Gradle build result")
    if "Reusing configuration cache" in log:
        raise ValueError("The fresh checkout did not store a fresh configuration cache")
    if configuration_cache:
        if "Configuration cache entry stored" not in log:
            raise ValueError("The fresh checkout did not store a fresh configuration cache")
    elif (
        "Configuration cache entry stored" in log
        or "MUZZLE_BENCHMARK_CONFIGURATION_CACHE requested=false" not in log
    ):
        raise ValueError("The candidate did not disable configuration caching")
    if "MUZZLE_BENCHMARK_REMOTE enabled=true push=false" not in log:
        raise ValueError("Remote-cache read-only access was not confirmed")
    if connection_ttl_ms is not None and f"MUZZLE_BENCHMARK_HTTP_CONNECTION_TTL {connection_ttl_ms}\n" not in log:
        raise ValueError("The candidate HTTP connection lifetime was not confirmed")
    tasks = benchmark.task_outcomes(log)
    assertions = {task: outcome for task, outcome in tasks.items() if ":muzzle-Assert" in task}
    benchmark.require_equal(
        {task: "EXECUTED" for task in actual}, assertions, "Executed assertions",
    )
    return tasks


def validate_diagnostics(directory, sample):
    if "operations" in sample:
        benchmark.require_equal(
            sample["operations"],
            operation_profile.summarize(directory / "operations.jsonl", sample["command_ms"]),
            "Captured build operations",
        )
    observed = sample["discovery"]
    metadata = benchmark.inventory(directory / "metadata")
    benchmark.require_equal(observed["metadata_sha256"], metadata, "Captured version metadata")
    expected_xml = {
        project.removeprefix(":").replace(":", "/") + "/build/muzzleRepo/" + document
        for project, documents in observed["projects"].items() for document in documents
    }
    actual_xml = {
        name for name in metadata if Path(name).name.startswith("maven-metadata-") and name.endswith(".xml")
    }
    benchmark.require_equal(expected_xml, actual_xml, "Discovery documents")
    if (
        observed["metadata_documents"] != len(expected_xml)
        or observed["project_count"] != len(observed["projects"])
    ):
        raise ValueError("Invalid discovery counts")
    profiles = list((directory / "profile").glob("profile-*.html"))
    if len(profiles) != 1:
        raise ValueError("Missing or repeated captured Gradle profile")
    benchmark.require_equal(sample["profile"], profile_metrics(profiles[0]), "Captured profile")


def paired_values(samples, metric):
    values = {sample["label"]: sample[metric] for sample in samples}
    if set(values) != set(LABELS) or len(samples) != 3:
        raise ValueError("A pair requires exactly one baseline, control, and candidate")
    return {
        label: {
            "delta_ms": values[label] - values["baseline"],
            "percent": (values[label] / values["baseline"] - 1) * 100,
        }
        for label in ("control", "candidate")
    }


def spread(values):
    return {
        "median": benchmark.statistics.median(values), "min": min(values), "max": max(values),
        "samples": len(values),
        "stdev": benchmark.statistics.stdev(values) if len(values) > 1 else 0,
    }


class Comparison:
    def __init__(self, args):
        self.args = args
        self.source = args.repository.resolve()
        self.root = args.work_dir.resolve()
        if (
            self.root.exists() or self.root.is_relative_to(self.source)
            or self.source.is_relative_to(self.root)
        ):
            raise ValueError("--work-dir must be new and outside the source checkout")
        self.root.mkdir(parents=True)
        self.reports = self.root / "reports"
        self.reports.mkdir()
        self.samples = []
        self.snapshot = args.cache_snapshot.resolve()
        if benchmark.digest(self.snapshot) != args.snapshot_sha256:
            raise ValueError("The cache snapshot SHA-256 does not match the prepared artifact")
        self.candidate_snapshot = getattr(args, "candidate_cache_snapshot", None)
        candidate_digest = getattr(args, "candidate_snapshot_sha256", None)
        if bool(self.candidate_snapshot) != bool(candidate_digest):
            raise ValueError("Both candidate cache snapshot and SHA-256 are required")
        if self.candidate_snapshot and benchmark.digest(self.candidate_snapshot) != candidate_digest:
            raise ValueError("The candidate cache snapshot SHA-256 does not match")
        self.revisions = {
            label: self.git("rev-parse", "--verify", "--end-of-options", f"{ref}^{{commit}}").decode().strip()
            for label, ref in (("baseline", args.baseline), ("candidate", args.candidate))
        }
        if self.candidate_snapshot and len(set(self.revisions.values())) != 1:
            raise ValueError("Dependency-cache comparisons require identical source revisions")
        runtime = {
            path: hashlib.sha256(self.git("show", f"{self.revisions['baseline']}:{path}")).hexdigest()
            for path in benchmark.RUNTIME_FILES
        }
        for path, expected in runtime.items():
            benchmark.require_equal(
                expected,
                hashlib.sha256(self.git("show", f"{self.revisions['candidate']}:{path}")).hexdigest(),
                path,
            )
        fixtures = {
            label: benchmark.fixture_identity(self.git("ls-tree", "-rz", ref, "--", "instrumentation"))
            for label, ref in self.revisions.items()
        }
        benchmark.require_equal(fixtures["baseline"], fixtures["candidate"], "Instrumentation sources")
        support = {
            label: hashlib.sha256(self.git("show", f"{ref}:{benchmark.CONTROLS.as_posix()}")).hexdigest()
            for label, ref in self.revisions.items()
        }
        benchmark.require_equal(support["baseline"], support["candidate"], "Benchmark support")
        java = shutil.which("java")
        if java is None:
            raise ValueError("java is missing from PATH")
        java = Path(java).resolve()
        self.env = dict(os.environ, JAVA_HOME=str(java.parent.parent))
        if self.env.get("DEVELOCITY_ACCESS_KEY"):
            raise ValueError("Do not provide remote-cache writer credentials to the benchmark")
        java_version = benchmark.run([str(java), "--version"], env=self.env).decode()
        required_java = self.git("show", f"{self.revisions['baseline']}:.java-version").decode().strip()
        version = re.search(r"\b(?:openjdk|java) (\d+)", java_version)
        if version is None or version[1] != required_java.split(".")[0]:
            raise ValueError(f"PATH must select JDK {required_java}")
        workload = json.loads(Path(__file__).with_name("workload.json").read_text(encoding="utf-8"))
        workload["seed"] = getattr(args, "seed", workload["seed"])
        self.identity = {
            "schema": 1,
            "revisions": self.revisions,
            "harness_revision": self.git("rev-parse", "HEAD").decode().strip(),
            "partition": args.partition,
            "round": args.round,
            "workload": workload,
            "cache_snapshot_sha256": args.snapshot_sha256,
            "runtime": runtime,
            "instrumentation_sources_sha256": fixtures["baseline"],
            "benchmark_support_sha256": support["baseline"],
            "init_script_sha256": benchmark.digest(INIT_SCRIPT),
            "conditions": "fresh checkout and Gradle home; live metadata; read-only remote cache",
            "java": java_version,
            "platform": platform.platform(),
            "cpu_count": os.cpu_count(),
            "runner_image": os.environ.get("ImageVersion"),
            "workflow_run": os.environ.get("GITHUB_RUN_ID"),
            "preparation_policy": os.environ.get("MUZZLE_PREPARATION_POLICY", "unrecorded"),
        }
        if self.candidate_snapshot:
            self.identity["experiment"] = "dependency-cache"
            self.identity["candidate_cache_snapshot_sha256"] = candidate_digest
        if getattr(args, "operations_profile", False):
            self.identity["operation_profiler_sha256"] = benchmark.digest(
                Path(__file__).with_name("operations.init.gradle"),
            )
        if getattr(args, "single_profile", False):
            self.identity["experiment"] = "operation-profile"
        if getattr(args, "jfr_network", False):
            self.identity["network_profiler_sha256"] = benchmark.digest(
                Path(__file__).with_name("network.init.gradle"),
            )
        if getattr(args, "candidate_no_configuration_cache", False):
            if self.candidate_snapshot or len(set(self.revisions.values())) != 1:
                raise ValueError("Configuration-cache comparisons require identical sources and snapshots")
            self.identity["experiment"] = "configuration-cache"
        ttl = getattr(args, "candidate_http_connection_ttl_ms", None)
        if ttl is not None:
            if (
                self.candidate_snapshot or len(set(self.revisions.values())) != 1
                or getattr(args, "candidate_no_configuration_cache", False)
            ):
                raise ValueError("Connection-lifetime comparisons require identical sources/snapshots and configuration-cache modes")
            self.identity["experiment"] = "http-connection-ttl"
            self.identity["candidate_http_connection_ttl_ms"] = ttl
        if getattr(args, "compare_slice_discovery", False):
            if (
                self.candidate_snapshot or len(set(self.revisions.values())) != 1
                or getattr(args, "candidate_no_configuration_cache", False) or ttl is not None
            ):
                raise ValueError("Slice-discovery comparisons require identical sources/snapshots and other settings")
            self.identity["experiment"] = "slice-discovery"
        if getattr(args, "muzzle_slices", None) is not None:
            self.identity["muzzle_slices"] = args.muzzle_slices
        write_json(self.reports / "identity.json", self.identity)
        self.persist()

    def git(self, *arguments):
        return benchmark.run(["git", "-C", str(self.source), *arguments])

    def persist(self, complete=False):
        write_json(self.reports / "samples.json", {"complete": complete, "samples": self.samples})

    def execute(self, label, *, retain_home=False):
        sample_root = self.root / label
        sample_root.mkdir()
        checkout = sample_root / "checkout"
        home = sample_root / "gradle-home"
        destination = self.reports / label
        destination.mkdir()
        revision = self.revisions["baseline" if label == "control" else label]
        sample = {"label": label, "revision": revision, "process_success": False}
        sample["configuration_cache"] = not (
            label == "candidate" and getattr(self.args, "candidate_no_configuration_cache", False)
        )
        sample["http_connection_ttl_ms"] = (
            getattr(self.args, "candidate_http_connection_ttl_ms", None) if label == "candidate" else None
        )
        sample["disk_free_before_checkout"] = shutil.disk_usage(sample_root).free
        sample["discover_all"] = (
            label != "candidate" if getattr(self.args, "compare_slice_discovery", False) else None
        )
        self.samples.append(sample)
        self.persist()
        started = time.perf_counter()
        benchmark.run(["git", "clone", "--shared", "--quiet", "--no-checkout", str(self.source), str(checkout)])
        benchmark.run(["git", "-C", str(checkout), "checkout", "--quiet", "--detach", revision])
        sample["checkout_ms"] = (time.perf_counter() - started) * 1000
        started = time.perf_counter()
        selected_snapshot = (
            getattr(self, "candidate_snapshot", None) if label == "candidate" else None
        ) or self.snapshot
        restore_snapshot(selected_snapshot, home)
        sample["cache_restore_ms"] = (time.perf_counter() - started) * 1000
        if list(checkout.rglob("muzzleRepo")) or list(checkout.rglob("configuration-cache")):
            raise ValueError("The checkout already contains metadata or a configuration cache")
        env = dict(self.env, GRADLE_USER_HOME=str(home))
        workload = self.identity["workload"]
        command = [
            str(checkout / ("gradlew.bat" if os.name == "nt" else "gradlew")),
            f":instrumentation:muzzle{self.args.partition}",
            "--no-daemon", "--no-scan", "--console=plain", "--profile",
            "--init-script", str(INIT_SCRIPT),
            f"-PmuzzleBenchmarkSeed={workload['seed']}",
            f"-Dotel.javaagent.muzzle.versions.limit={workload['versions_limit']}",
            f"-PmuzzleBenchmarkReportDirectory={destination / 'assertions'}",
        ]
        if getattr(self.args, "operations_profile", False):
            command += [
                "--init-script", str(Path(__file__).with_name("operations.init.gradle")),
                f"-Dmuzzle.profile.output={destination / 'operations.jsonl'}",
            ]
        if getattr(self.args, "jfr_network", False):
            command += [
                "--init-script", str(Path(__file__).with_name("network.init.gradle")),
                f"-Dmuzzle.profile.jfr={destination / 'network.jfr'}",
            ]
        if not sample["configuration_cache"]:
            command.append("--no-configuration-cache")
        if sample["http_connection_ttl_ms"] is not None:
            command.append(f"-Dorg.gradle.internal.http.idleConnectionTimeout={sample['http_connection_ttl_ms']}")
        if sample["discover_all"] is not None:
            command.append(f"-PmuzzleBenchmarkDiscoverAll={str(sample['discover_all']).lower()}")
        if getattr(self.args, "muzzle_slices", None) is not None:
            command.append(f"-PmuzzleSlices={self.args.muzzle_slices}")
        write_json(destination / "command.json", command)
        started = time.perf_counter()
        started_wall = int(time.time() * 1000)
        try:
            stop = threading.Event()
            with ThreadPoolExecutor(max_workers=1) as pool:
                monitor = pool.submit(sample_disk_usage, checkout, stop)
                try:
                    benchmark.run(command, cwd=checkout, env=env, log=destination / "console.log")
                    sample["process_success"] = True
                finally:
                    stop.set()
                    sample["disk"] = monitor.result()
        finally:
            sample["command_ms"] = (time.perf_counter() - started) * 1000
            self.persist()
            print(f"{label}: {sample['command_ms'] / 1000:.2f}s; success={sample['process_success']}", flush=True)
        actual = benchmark.manifests(destination / "assertions")
        log = (destination / "console.log").read_text(encoding="utf-8", errors="replace")
        tasks = validate_build(
            log, actual, configuration_cache=sample["configuration_cache"],
            connection_ttl_ms=sample["http_connection_ttl_ms"],
        )
        if sample["discover_all"] is not None:
            expected = f"MUZZLE_BENCHMARK_DISCOVER_ALL {str(sample['discover_all']).lower()}\n"
            if expected not in log:
                raise ValueError("Version discovery mode was not confirmed")
        if getattr(self.args, "jfr_network", False):
            recording = destination / "network.jfr"
            if (
                "MUZZLE_BENCHMARK_JFR flushed=true" not in log
                or not recording.is_file() or recording.stat().st_size == 0
            ):
                raise ValueError("Missing Flight Recorder network profile")
            sample["network_jfr_sha256"] = benchmark.digest(recording)
        sample["task_outcomes"] = tasks
        sample["task_outcome_counts"] = dict(Counter(tasks.values()))
        sample["assertions"] = len(actual)
        graph = re.findall(r"^MUZZLE_BENCHMARK_TASK_GRAPH (\d+)$", log, re.MULTILINE)
        if len(graph) != 1:
            raise ValueError("Missing or repeated main-build task-graph timing marker")
        sample["time_to_task_graph_ms"] = int(graph[0]) - started_wall
        if not 0 <= sample["time_to_task_graph_ms"] <= sample["command_ms"] + 1000:
            raise ValueError("Invalid task-graph timestamp")
        profiles = list((checkout / "build" / "reports" / "profile").glob("profile-*.html"))
        if len(profiles) != 1:
            raise ValueError("Expected exactly one main-build Gradle profile")
        sample["profile"] = profile_metrics(profiles[0])
        if sample["profile"]["assertion_task_count"] != len(actual):
            raise ValueError("The Gradle profile does not cover every executed assertion")
        shutil.copytree(profiles[0].parent, destination / "profile")
        sample["discovery"] = discovery(checkout, destination)
        sample["setup_ms"] = sample["checkout_ms"] + sample["cache_restore_ms"]
        if getattr(self.args, "operations_profile", False):
            sample["operations"] = operation_profile.summarize(
                destination / "operations.jsonl", sample["command_ms"],
            )
        self.persist()
        # Only these newly created per-sample directories are disposable.
        shutil.rmtree(checkout)
        if not retain_home:
            shutil.rmtree(home)
        return home

    def measure(self):
        for label in benchmark.order(self.args.round - 1):
            self.execute(label)
        reference = benchmark.manifests(self.reports / "baseline" / "assertions")
        for label in ("control", "candidate"):
            benchmark.require_equal(
                reference, benchmark.manifests(self.reports / label / "assertions"),
                f"{label} assertion/dependency workload",
            )
        write_json(self.reports / "paired.json", paired_values(self.samples, "command_ms"))
        self.persist(complete=True)


def artifact_path(root, artifact, filename, *, single=False):
    nested = root / artifact / filename
    flat = root / filename
    if single and flat.is_file():
        if nested.is_file():
            raise ValueError("Ambiguous artifact layout")
        return flat
    return nested


def load_reports(root, rounds, partitions):
    reports = {}
    shared = None
    expected_revisions = None
    references = {}
    candidate_snapshots = {}
    for partition in partitions:
        for round_number in range(1, rounds + 1):
            identity_path = artifact_path(
                root, f"muzzle-ci-{round_number}-{partition}", "identity.json",
                single=rounds == 1 and len(partitions) == 1,
            )
            directory = identity_path.parent
            identity = json.loads(identity_path.read_text(encoding="utf-8"))
            result = json.loads((directory / "samples.json").read_text(encoding="utf-8"))
            if not result["complete"] or identity["partition"] != partition or identity["round"] != round_number:
                raise ValueError(f"Incomplete or misidentified comparison: {directory}")
            common = {
                key: value for key, value in identity.items()
                if key not in {"partition", "round", "platform", "candidate_cache_snapshot_sha256"}
            }
            if identity.get("experiment") == "dependency-cache":
                if len(set(identity["revisions"].values())) != 1:
                    raise ValueError("Dependency-cache reports require identical source revisions")
                value = identity["candidate_cache_snapshot_sha256"]
                if partition in candidate_snapshots:
                    benchmark.require_equal(candidate_snapshots[partition], value, "Warmed cache across rounds")
                candidate_snapshots[partition] = value
            if identity.get("experiment") in {"configuration-cache", "http-connection-ttl", "slice-discovery"}:
                if (
                    len(set(identity["revisions"].values())) != 1
                    or "candidate_cache_snapshot_sha256" in identity
                ):
                    raise ValueError("Build-setting reports require identical sources and snapshots")
            if shared is None:
                shared = common
                expected_revisions = identity["revisions"]
            benchmark.require_equal(shared, common, "Cross-job benchmark conditions")
            expected_order = list(benchmark.order(round_number - 1))
            if [sample["label"] for sample in result["samples"]] != expected_order:
                raise ValueError("Comparison order or sample count changed")
            if shared.get("experiment") == "slice-discovery":
                by_label = {sample["label"]: sample for sample in result["samples"]}
                baseline = by_label["baseline"]
                for label in ("control", "candidate"):
                    benchmark.require_equal(
                        baseline["task_outcomes"], by_label[label]["task_outcomes"],
                        "Discovery experiment task-cache outcomes",
                    )
                benchmark.require_equal(
                    baseline["discovery"]["projects"], by_label["control"]["discovery"]["projects"],
                    "Eager discovery control",
                )
                candidate_projects = by_label["candidate"]["discovery"]["projects"]
                if not candidate_projects.keys() < baseline["discovery"]["projects"].keys():
                    raise ValueError("Slice-aware discovery did not reduce the discovery project set")
                for project, documents in candidate_projects.items():
                    benchmark.require_equal(
                        baseline["discovery"]["projects"][project], documents,
                        "Selected-project metadata paths",
                    )
            for sample in result["samples"]:
                label = sample["label"]
                revision = expected_revisions["baseline" if label == "control" else label]
                if not sample["process_success"] or sample["revision"] != revision:
                    raise ValueError("Failed or misidentified sample")
                actual = benchmark.manifests(directory / label / "assertions")
                if sample["assertions"] != len(actual):
                    raise ValueError("Invalid assertion count")
                if partition not in references:
                    references[partition] = benchmark.manifests(directory / "baseline" / "assertions")
                benchmark.require_equal(references[partition], actual, "Cross-round workload")
                log = (directory / label / "console.log").read_text(encoding="utf-8", errors="replace")
                benchmark.require_equal(
                    sample["task_outcomes"],
                    validate_build(
                        log, actual, configuration_cache=sample.get("configuration_cache", True),
                        connection_ttl_ms=sample.get("http_connection_ttl_ms"),
                    ),
                    "Recorded task outcomes",
                )
                expected_cache = not (shared.get("experiment") == "configuration-cache" and label == "candidate")
                if sample.get("configuration_cache", True) != expected_cache:
                    raise ValueError("Configuration-cache mode does not match the experiment")
                expected_ttl = shared.get("candidate_http_connection_ttl_ms") if label == "candidate" else None
                if sample.get("http_connection_ttl_ms") != expected_ttl:
                    raise ValueError("Connection-lifetime mode does not match the experiment")
                if shared.get("experiment") == "slice-discovery":
                    expected_discovery = label != "candidate"
                    if (
                        sample.get("discover_all") != expected_discovery
                        or f"MUZZLE_BENCHMARK_DISCOVER_ALL {str(expected_discovery).lower()}\n" not in log
                    ):
                        raise ValueError("Version discovery mode does not match the experiment")
                validate_diagnostics(directory / label, sample)
            reports[(round_number, partition)] = result["samples"]
    if candidate_snapshots:
        shared = dict(shared, candidate_cache_snapshots=candidate_snapshots)
    return shared, references, reports


def aggregate(root, destination, rounds, partitions):
    shared, references, reports = load_reports(root, rounds, partitions)
    destination.mkdir(parents=True, exist_ok=True)
    maxima = []
    slices = {}
    for partition in partitions:
        by_round = [reports[(round_number, partition)] for round_number in range(1, rounds + 1)]
        slices[str(partition)] = {
            "assertions": len(references[partition]),
            "medians_ms": {
                label: benchmark.statistics.median(
                    next(sample["command_ms"] for sample in samples if sample["label"] == label)
                    for samples in by_round
                )
                for label in LABELS
            },
            "paired": {
                label: {
                    field: spread([
                        paired_values(samples, "command_ms")[label][field] for samples in by_round
                    ])
                    for field in ("delta_ms", "percent")
                }
                for label in ("control", "candidate")
            },
        }
    for round_number in range(1, rounds + 1):
        samples = []
        for label in LABELS:
            values = [
                next(sample["command_ms"] for sample in reports[(round_number, partition)] if sample["label"] == label)
                for partition in partitions
            ]
            samples.append({"label": label, "command_ms": max(values)})
        maxima.append({"round": round_number, "samples": samples, "paired": paired_values(samples, "command_ms")})
    result = {
        "complete": True, "identity": shared, "rounds": rounds, "partitions": partitions,
        "slice_results": slices, "maximum_slice_rounds": maxima,
        "maximum_slice_medians_ms": {
            label: benchmark.statistics.median(
                next(sample["command_ms"] for sample in row["samples"] if sample["label"] == label)
                for row in maxima
            )
            for label in LABELS
        },
        "maximum_slice_paired": {
            label: {
                field: spread([row["paired"][label][field] for row in maxima])
                for field in ("delta_ms", "percent")
            }
            for label in ("control", "candidate")
        },
        "setup_medians_ms": {
            label: {
                field: benchmark.statistics.median(
                    sample[field] for samples in reports.values() for sample in samples
                    if sample["label"] == label
                )
                for field in ("checkout_ms", "cache_restore_ms")
            }
            for label in LABELS
        },
    }
    write_json(destination / "results.json", result)
    lines = [
        "# Fresh-checkout Muzzle comparison", "",
        f"Baseline `{shared['revisions']['baseline']}`; candidate `{shared['revisions']['candidate']}`.",
        "",
        "Fresh checkouts and independent Gradle homes restored from "
        + ("condition-specific immutable snapshots. " if shared.get("experiment") == "dependency-cache"
           else "one immutable snapshot. ")
        + "Live Aether metadata, online dependencies, read-only remote cache, and no assertion caching.",
        "",
        "The primary metric is the maximum Gradle command duration across slices in each round. "
        "It is not the observed Actions fan-out completion time or total PR time. "
        "Checkout and cache extraction are recorded separately and excluded from command time.",
        "",
        "| Slice | Assertions | Baseline median | Control median | Candidate median | Paired candidate change |",
        "| --- | ---: | ---: | ---: | ---: | ---: |",
    ]
    if shared.get("experiment") == "dependency-cache":
        lines[4:4] = [
            "Both sides use identical source code. Only the candidate's Gradle dependency cache "
            "was populated by earlier, differently seeded Muzzle samples. All non-dependency "
            "snapshot entries are preserved from the original snapshot.",
            "",
        ]
    if shared.get("experiment") == "configuration-cache":
        lines[4:4] = [
            "Both sides use identical source code and cache snapshots. Baseline and control store "
            "a fresh configuration cache; candidate disables configuration caching. "
            "Local and remote task-cache reads remain enabled in every command.",
            "",
        ]
    if shared.get("experiment") == "http-connection-ttl":
        lines[4:4] = [
            "Both sides use identical source code, snapshots and configuration-cache modes. "
            f"Only candidate sets the HTTP connection maximum lifetime to {shared['candidate_http_connection_ttl_ms']} ms. "
            "Connection/read timeouts, retries, dependency selection and task-cache settings are unchanged.",
            "",
        ]
    if shared.get("experiment") == "slice-discovery":
        lines[4:4] = [
            "Both sides use identical source code, compiled build logic and cache snapshots. "
            "Baseline and control discover versions in every Muzzle project. Candidate discovers "
            "only projects belonging to the requested slice. The executed compatibility workload "
            "must remain identical.",
            "",
        ]
    for partition, entry in slices.items():
        times = entry["medians_ms"]
        lines.append(
            f"| {partition} | {entry['assertions']} | {times['baseline'] / 1000:.2f}s "
            f"| {times['control'] / 1000:.2f}s | {times['candidate'] / 1000:.2f}s "
            f"| {entry['paired']['candidate']['percent']['median']:+.2f}% |"
        )
    lines += [
        "", "## Slowest slice", "",
        "| Round | Baseline maximum | Control maximum | Candidate maximum |",
        "| --- | ---: | ---: | ---: |",
    ]
    for row in maxima:
        values = {sample["label"]: sample["command_ms"] for sample in row["samples"]}
        lines.append(
            f"| {row['round']} | {values['baseline'] / 1000:.2f}s "
            f"| {values['control'] / 1000:.2f}s | {values['candidate'] / 1000:.2f}s |"
        )
    lines.append("")
    for label in ("candidate", "control"):
        paired = result["maximum_slice_paired"][label]
        lines.append(
            f"- {label.capitalize()} median paired change: {paired['delta_ms']['median'] / 1000:+.2f}s "
            f"and {paired['percent']['median']:+.2f}%. "
            f"Paired time range: {paired['delta_ms']['min'] / 1000:+.2f}s "
            f"to {paired['delta_ms']['max'] / 1000:+.2f}s."
        )
    lines += [
        "",
        "All assertion/dependency records must match across every revision and round. "
        "No failed builds or outliers are retried or removed. "
        "Main builds may populate the shared remote cache during the experiment. "
        "Compare the candidate effect with the identical-revision control before claiming a speedup.",
        "",
        "Gradle profiles, time to the main task graph, task outcomes, version metadata, setup times, "
        "and complete assertion records are retained with each sample. "
        "Profile assertion durations are sums of task durations, not parallel execution wall time. "
        "Time to the main task graph also includes startup and included-build work, not only discovery.",
        "",
        "## Per-sample setup", "",
        "These benchmark preparation costs are excluded from the Gradle comparison. "
        "Actions runner allocation, Java setup, and snapshot download are also excluded.",
        "",
        "| Revision | Median checkout | Median cache extraction |",
        "| --- | ---: | ---: |",
        *[
            f"| {label} | {values['checkout_ms'] / 1000:.2f}s "
            f"| {values['cache_restore_ms'] / 1000:.2f}s |"
            for label, values in result["setup_medians_ms"].items()
        ],
    ]
    if rounds < 5 or partitions != [1, 2, 3, 4]:
        lines += ["", "**Functional run only. Five rounds and all four slices are required for the primary comparison.**"]
    (destination / "summary.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    capture = commands.add_parser("snapshot")
    capture.add_argument("--gradle-home", type=Path, required=True)
    capture.add_argument("--output", type=Path, required=True)
    measure = commands.add_parser("run")
    measure.add_argument("--repository", type=Path, default=Path(__file__).resolve().parents[1])
    measure.add_argument("--baseline", required=True)
    measure.add_argument("--candidate", required=True)
    measure.add_argument("--partition", type=int, choices=range(1, 5), required=True)
    measure.add_argument("--round", type=int, choices=range(1, 11), required=True)
    measure.add_argument("--work-dir", type=Path, required=True)
    measure.add_argument("--cache-snapshot", type=Path, required=True)
    measure.add_argument("--snapshot-sha256", required=True)
    measure.add_argument("--candidate-cache-snapshot", type=Path)
    measure.add_argument("--candidate-snapshot-sha256")
    measure.add_argument("--operations-profile", action="store_true")
    measure.add_argument("--single-profile", action="store_true")
    measure.add_argument("--jfr-network", action="store_true")
    measure.add_argument("--compare-slice-discovery", action="store_true")
    measure.add_argument("--candidate-no-configuration-cache", action="store_true")
    measure.add_argument("--candidate-http-connection-ttl-ms", type=int, choices=(10000,))
    report = commands.add_parser("report")
    report.add_argument("--inputs", type=Path, required=True)
    report.add_argument("--output", type=Path, required=True)
    report.add_argument("--rounds", type=int, choices=range(1, 11), required=True)
    report.add_argument("--partitions", type=int, choices=range(1, 5), nargs="+", required=True)
    args = parser.parse_args()
    if args.command == "snapshot":
        print(snapshot(args.gradle_home, args.output))
    elif args.command == "report":
        if args.partitions != sorted(set(args.partitions)):
            parser.error("--partitions must be sorted and unique")
        aggregate(args.inputs, args.output, args.rounds, args.partitions)
    else:
        if sys.platform != "linux":
            parser.error("Build measurements run on Linux; unit tests also support Windows")
        runner = Comparison(args)
        try:
            if args.single_profile:
                if not args.operations_profile:
                    parser.error("--single-profile requires --operations-profile")
                runner.execute(
                    "candidate" if args.candidate_no_configuration_cache or args.candidate_http_connection_ttl_ms else "baseline"
                )
                runner.persist(complete=True)
            else:
                runner.measure()
        except (OSError, ValueError, tarfile.TarError, subprocess.CalledProcessError) as error:
            (runner.reports / "failure.txt").write_text(str(error) + "\n", encoding="utf-8")
            runner.persist()
            raise


if __name__ == "__main__":
    main()
