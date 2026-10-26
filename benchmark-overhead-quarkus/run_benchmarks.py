# Copyright The OpenTelemetry Authors
# SPDX-License-Identifier: Apache-2.0

"""Run Bruno Baptista's Quarkus database workload with explicit agent artifacts."""

import argparse
import copy
import hashlib
import itertools
import json
import math
import os
import platform
import shutil
import signal
import statistics
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
from pathlib import Path

import yaml

HERE = Path(__file__).resolve().parent
APP_URL = "http://127.0.0.1:8080"
LOAD_URL = "http://127.0.0.1:8090"
TELEMETRY_URL = "http://127.0.0.1:14318"
PG_BIN = Path("/usr/lib/postgresql/16/bin")
PARENT_ID = "1122334455667788"


def run(command, *, check=True, **kwargs):
    return subprocess.run(
        [str(value) for value in command],
        check=check,
        creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0,
        **kwargs,
    )


def spawn(command, **kwargs):
    return subprocess.Popen(
        [str(value) for value in command],
        creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0,
        start_new_session=os.name != "nt",
        **kwargs,
    )


def save(path, value):
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def http(url, body=None, content_type="application/json", method=None, headers=None):
    request = urllib.request.Request(
        url,
        data=body,
        method=method,
        headers={"Content-Type": content_type, **(headers or {})},
    )
    try:
        with urllib.request.urlopen(request, timeout=15) as response:
            return response.status, response.read()
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"HTTP {error.code} from {url}: {detail}") from error


def get_json(url):
    return json.loads(http(url)[1])


def wait_ready(url, processes, timeout=120):
    end = time.monotonic() + timeout
    while time.monotonic() < end:
        processes.check()
        try:
            return http(url)
        except urllib.error.URLError:
            time.sleep(0.5)
    raise TimeoutError(f"Service did not become ready: {url}")


def select_cpus(topology, allowed):
    nodes = {}
    for line in topology.splitlines():
        if not line or line.startswith("#"):
            continue
        cpu, core, socket, node, online = line.split(",")
        if online == "Y" and int(cpu) in allowed:
            nodes.setdefault((socket, node), {}).setdefault(core, int(cpu))
    for node in sorted(nodes):
        cpus = sorted(nodes[node].values())
        if len(cpus) >= 12:
            return {
                "app": cpus[:2],
                "database": cpus[2:6],
                "load": cpus[6:10],
                "receiver": cpus[10:11],
                "driver": cpus[11:12],
            }
    raise ValueError("Twelve allowed physical cores on one NUMA node are required")


def schedule(repetitions):
    for repetition in range(repetitions):
        yield repetition, "no-agent"
        order = (
            ("baseline", "candidate")
            if repetition % 2 == 0
            else ("candidate", "baseline")
        )
        for agent in order:
            yield repetition, agent


def workload(source, rate, warmup, duration):
    result = copy.deepcopy(source)
    if result["name"] != "quarkus-activemq":
        raise ValueError("Unexpected upstream Hyperfoil workload")
    result["http"][0]["host"] = APP_URL
    for phase in result["phases"]:
        if "rampup" in phase:
            ramp = phase["rampup"]["increasingRate"]
            ramp.update(
                initialUsersPerSec=1,
                targetUsersPerSec=rate,
                duration=f"{warmup}s",
            )
        if "mainPhase" in phase:
            main = phase["mainPhase"]["constantRate"]
            main.update(usersPerSec=rate, duration=f"{duration}s", maxSessions=10000)
            main.pop("startAfter", None)
            main["startAfterStrict"] = "rampup"
        for model in next(iter(phase.values())).values():
            for fork in model.get("forks", {}).values():
                for sequence in fork["scenario"]:
                    for step in next(iter(sequence.values())):
                        if "httpRequest" in step:
                            step["httpRequest"]["authority"] = "127.0.0.1:8080"
            for sequence in model.get("scenario", []):
                for step in next(iter(sequence.values())):
                    if "httpRequest" in step:
                        step["httpRequest"]["authority"] = "127.0.0.1:8080"
    return result


class Processes:
    def __init__(self, output, cpus):
        self.output = output
        self.cpus = cpus
        self.processes = {}
        self.logs = []
        self.commands = {}

    def start(self, name, command, **kwargs):
        log = (self.output / f"{name}.log").open("w", encoding="utf-8")
        self.logs.append(log)
        command = ["taskset", "--cpu-list", ",".join(map(str, self.cpus[name]))] + [
            str(value) for value in command
        ]
        self.commands[name] = command
        save(self.output / "commands.json", self.commands)
        process = spawn(command, stdout=log, stderr=subprocess.STDOUT, **kwargs)
        self.processes[name] = process
        return process

    def check(self):
        for name, process in self.processes.items():
            if process.poll() is not None:
                raise RuntimeError(
                    f"{name} exited with {process.returncode}; see its log"
                )

    def stop(self, name):
        process = self.processes.pop(name)
        if process.poll() is None:
            os.killpg(process.pid, signal.SIGTERM)
            try:
                process.wait(timeout=20)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGKILL)
                process.wait(timeout=10)

    def __enter__(self):
        return self

    def __exit__(self, *_args):
        for name in reversed(list(self.processes)):
            self.stop(name)
        for log in self.logs:
            log.close()


def process_counters(pid):
    for attempt in range(5):
        try:
            return read_process_counters(pid)
        except (FileNotFoundError, ProcessLookupError) as error:
            if not Path(f"/proc/{pid}").exists():
                raise
            if attempt == 4:
                raise RuntimeError(
                    f"Could not read a stable process tree for PID {pid}"
                ) from error
            print(
                f"Resampling CPU counters after PID {pid}'s process tree changed",
                file=sys.stderr,
                flush=True,
            )
            time.sleep(0.01)


def read_process_counters(pid):
    stat = Path(f"/proc/{pid}/stat").read_text().split(") ", 1)[1].split()
    children = Path(f"/proc/{pid}/task/{pid}/children").read_text().split()
    cpu = sum(int(stat[index]) for index in (11, 12, 13, 14)) / os.sysconf("SC_CLK_TCK")
    rss = int(stat[21]) * os.sysconf("SC_PAGE_SIZE")
    for child in children:
        child_cpu, child_rss = read_process_counters(int(child))
        cpu += child_cpu
        rss += child_rss
    after_children = Path(f"/proc/{pid}/task/{pid}/children").read_text().split()
    after_stat = Path(f"/proc/{pid}/stat").read_text().split(") ", 1)[1].split()
    if children != after_children or stat[13:15] != after_stat[13:15]:
        raise ProcessLookupError(f"PID {pid}'s child processes changed during sampling")
    return cpu, rss


def snapshot(processes):
    values = {name: process_counters(p.pid) for name, p in processes.processes.items()}
    return {"time_ms": time.time_ns() / 1_000_000, "processes": values}


def interpolate(samples, name, timestamp):
    for left, right in itertools.pairwise(samples):
        if left["time_ms"] <= timestamp <= right["time_ms"]:
            fraction = (timestamp - left["time_ms"]) / (
                right["time_ms"] - left["time_ms"]
            )
            return left["processes"][name][0] + fraction * (
                right["processes"][name][0] - left["processes"][name][0]
            )
    raise ValueError(
        f"CPU observations do not cover the measurement boundary: {timestamp}"
    )


def persisted_result(output, run_id, processes):
    """Wait for the asynchronous statistics write that follows phase termination."""
    path = output / "hyperfoil" / "run" / run_id / "all.json"
    deadline = time.monotonic() + 30
    while time.monotonic() < deadline:
        processes.check()
        if path.is_file():
            try:
                return json.loads(path.read_text(encoding="utf-8"))
            except json.JSONDecodeError:
                pass
        time.sleep(0.1)
    raise TimeoutError(f"Hyperfoil did not finish persisting its statistics: {path}")


def analyze(result, samples, cpus, rate, duration):
    groups = [entry for entry in result["stats"] if entry["phase"] == "mainPhase"]
    if {entry["fork"] for entry in groups} != {"createFork", "gettersFork"}:
        raise ValueError("Missing Hyperfoil measurement forks")
    summaries = [entry["total"]["summary"] for entry in groups]
    start = min(value["startTime"] for value in summaries)
    end = max(value["endTime"] for value in summaries)
    seconds = (end - start) / 1000
    errors = [failure["message"] for failure in result["failures"]]
    completed = sum(value["responseCount"] for value in summaries)
    requests = sum(value["requestCount"] for value in summaries)
    for value in summaries:
        if any(
            value[name]
            for name in (
                "invalid",
                "connectionErrors",
                "requestTimeouts",
                "internalErrors",
            )
        ):
            errors.append("Hyperfoil reported unsuccessful requests")
        if value["blockedTime"]:
            errors.append("Hyperfoil waited for a free connection")
    if completed != requests:
        errors.append("Some issued requests did not receive a response")
    expected = rate * duration
    tolerance = max(0.03 * expected, 6 * math.sqrt(expected))
    if abs(requests - expected) > tolerance:
        errors.append("Offered request count differs from the requested rate")
    if not 0.95 * duration <= seconds <= 1.1 * duration:
        errors.append("Unexpected measurement duration or excessive drain time")
    cpu_seconds = {
        name: interpolate(samples, name, end) - interpolate(samples, name, start)
        for name in cpus
        if name != "driver"
    }
    utilization = {
        name: value / seconds / len(cpus[name]) for name, value in cpu_seconds.items()
    }
    for name in ("database", "load", "receiver"):
        if utilization[name] >= 0.75:
            errors.append(f"{name} CPU utilization leaves insufficient headroom")
    if utilization["app"] >= 0.85:
        errors.append("Application CPU utilization leaves insufficient headroom")
    if completed <= 0:
        raise ValueError("No successful measurement requests")
    return {
        "valid": not errors,
        "errors": errors,
        "successful_requests": completed,
        "issued_requests": requests,
        "expected_database_rows": 4
        + sum(
            entry["total"]["summary"]["responseCount"]
            for entry in result["stats"]
            if entry["fork"] == "createFork" or entry["phase"] == "firstReq"
        ),
        "measurement_seconds": seconds,
        "cpu_us_per_request": cpu_seconds["app"] * 1_000_000 / completed,
        "cpu_seconds": cpu_seconds,
        "cpu_utilization": utilization,
        "max_rss_bytes": {
            name: max(sample["processes"][name][1] for sample in samples)
            for name in cpu_seconds
        },
        "latency": {
            entry["fork"]: {
                "mean_us": entry["total"]["summary"]["meanResponseTime"] / 1000,
                **{
                    f"p{percentile}_us": value / 1000
                    for percentile, value in entry["total"]["summary"][
                        "percentileResponseTime"
                    ].items()
                },
            }
            for entry in groups
        },
    }


def validate_spans(stats, agent):
    if stats["errors"]:
        raise ValueError("The OTLP receiver rejected export requests")
    if agent == "no-agent":
        if stats["kinds"]:
            raise ValueError("The no-agent application unexpectedly exported spans")
        return
    spans = stats["preflight"]
    for index in range(1, 5):
        trace_id = f"{index:032x}"
        trace = [span for span in spans if span["trace_id"] == trace_id]
        servers = [span for span in trace if span["kind"] == 2]
        database = [
            span
            for span in trace
            if span["kind"] == 3
            and (
                span["attributes"].get("db.system") == "postgresql"
                or span["attributes"].get("db.system.name") == "postgresql"
            )
        ]
        if len(servers) != 1 or servers[0]["parent_id"] != PARENT_ID or not database:
            raise ValueError(
                f"Expected one propagated HTTP server span and PostgreSQL spans: {trace_id}"
            )
        parents = {span["span_id"]: span["parent_id"] for span in trace}
        for span in database:
            parent = span["parent_id"]
            visited = set()
            while parent != servers[0]["span_id"] and parent in parents:
                if parent in visited:
                    raise ValueError("Cycle in the exported trace")
                visited.add(parent)
                parent = parents[parent]
            if parent != servers[0]["span_id"]:
                raise ValueError(
                    "Database spans are not children of the HTTP server span"
                )


def preflight(processes, agent):
    for index in range(1, 5):
        body = json.dumps(
            {"name": f"benchmark-preflight-{index}", "description": "correctness check"}
        ).encode()
        headers = {"traceparent": f"00-{index:032x}-{PARENT_ID}-01"}
        status, response = http(APP_URL + "/legumes", body=body, headers=headers)
        if status != 201 or not json.loads(response).get("id"):
            raise ValueError("Insert did not return a new record")
    records = json.loads(http(APP_URL + "/legumes?pageIndex=0")[1])
    if len(records) != 4:
        raise ValueError("The application did not read back its inserted records")
    end = time.monotonic() + (15 if agent != "no-agent" else 1)
    while time.monotonic() < end:
        processes.check()
        stats = get_json(TELEMETRY_URL + "/stats")
        complete = all(
            any(
                span["trace_id"] == f"{index:032x}" and span["kind"] == kind
                for span in stats["preflight"]
            )
            for index in range(1, 5)
            for kind in (2, 3)
        )
        if agent == "no-agent" or complete:
            validate_spans(stats, agent)
            return stats
        time.sleep(0.5)
    raise ValueError("SDK tracing was not exported under the measured configuration")


def app_command(java, artifacts, active_agent, trial, profile):
    command = [
        java,
        "-Xms512m",
        "-Xmx512m",
        "-XX:+UseG1GC",
        "-XX:ActiveProcessorCount=2",
        f"-Xlog:gc*:file={trial / 'gc.log'}:time,uptime,level,tags",
        "-Dquarkus.datasource.jdbc.url=jdbc:postgresql://127.0.0.1:15432/test?current_schema=public",
    ]
    if active_agent is not None:
        command.extend(
            [
                f"-javaagent:{active_agent}",
                "-Dotel.service.name=legume",
                "-Dotel.traces.sampler=traceidratio",
                "-Dotel.traces.sampler.arg=0.1",
                "-Dotel.traces.exporter=otlp",
                "-Dotel.exporter.otlp.traces.protocol=http/protobuf",
                f"-Dotel.exporter.otlp.traces.endpoint={TELEMETRY_URL}/v1/traces",
                "-Dotel.exporter.otlp.traces.compression=none",
                "-Dotel.metrics.exporter=none",
                "-Dotel.logs.exporter=none",
                "-Dotel.bsp.max.export.batch.size=512",
                "-Dotel.bsp.max.queue.size=2048",
                "-Dotel.bsp.schedule.delay=1000",
            ]
        )
    if profile:
        command.extend(
            [
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:+DebugNonSafepoints",
                (
                    "-XX:StartFlightRecording=settings=profile,dumponexit=true,"
                    f"filename={trial / 'application.jfr'}"
                ),
            ]
        )
    return command + ["-jar", artifacts / "quarkus-app" / "quarkus-run.jar"]


def execute(artifacts, output, cpus, java, agent, rate, warmup, duration, mode):
    output.mkdir()
    print(
        f"Starting {output.name}: {rate} requests/s, {warmup}s warmup, {duration}s measurement",
        flush=True,
    )
    active_agent = output.parent / "active-agent.jar"
    if agent != "no-agent":
        shutil.copyfile(artifacts / f"{agent}-agent.jar", active_agent)
    try:
        with tempfile.TemporaryDirectory(prefix="postgres-", dir=output) as directory:
            data = Path(directory)
            shutil.chown(data, user="postgres", group="postgres")
            run(
                [
                    "runuser",
                    "-u",
                    "postgres",
                    "--",
                    PG_BIN / "initdb",
                    "-D",
                    data,
                    "--auth=trust",
                    "--no-locale",
                    "--encoding=UTF8",
                    "--username=pguser",
                ],
                capture_output=True,
            )
            with Processes(output, cpus) as processes:
                processes.start(
                    "database",
                    [
                        "runuser",
                        "-u",
                        "postgres",
                        "--",
                        PG_BIN / "postgres",
                        "-D",
                        data,
                        "-h",
                        "127.0.0.1",
                        "-p",
                        "15432",
                        "-c",
                        "unix_socket_directories=",
                        "-c",
                        "shared_buffers=256MB",
                        "-c",
                        "max_connections=100",
                    ],
                )
                end = time.monotonic() + 30
                while True:
                    processes.check()
                    ready = run(
                        [str(PG_BIN / "pg_isready"), "-h", "127.0.0.1", "-p", "15432"],
                        capture_output=True,
                        check=False,
                    )
                    if ready.returncode == 0:
                        break
                    if ready.returncode not in (1, 2) or time.monotonic() >= end:
                        raise RuntimeError("PostgreSQL did not become ready")
                    time.sleep(0.5)
                run(
                    [
                        PG_BIN / "createdb",
                        "-h",
                        "127.0.0.1",
                        "-p",
                        "15432",
                        "-U",
                        "pguser",
                        "test",
                    ],
                    capture_output=True,
                )
                processes.start(
                    "receiver", [sys.executable, "-B", HERE / "telemetry_receiver.py"]
                )
                wait_ready(TELEMETRY_URL + "/stats", processes)
                processes.start(
                    "app",
                    app_command(
                        java,
                        artifacts,
                        active_agent if agent != "no-agent" else None,
                        output,
                        mode == "profile",
                    ),
                )
                wait_ready(APP_URL + "/legumes", processes)
                save(output / "preflight.json", preflight(processes, agent))
                distributions = list((artifacts / "hyperfoil").glob("*/lib"))
                if len(distributions) != 1:
                    raise ValueError("Expected one Hyperfoil distribution")
                distribution = distributions[0].parent
                classpath = os.pathsep.join(
                    str(path)
                    for folder in ("lib", "extensions")
                    for path in sorted((distribution / folder).rglob("*.jar"))
                )
                processes.start(
                    "load",
                    [
                        java,
                        "-Xms1g",
                        "-Xmx1g",
                        "-XX:ActiveProcessorCount=4",
                        "-Dio.hyperfoil.cpu.watchdog.idle.threshold=0",
                        "--add-opens=java.base/java.lang=ALL-UNNAMED",
                        f"-Dio.hyperfoil.rootdir={output / 'hyperfoil'}",
                        "-Dio.hyperfoil.controller.host=127.0.0.1",
                        "-Dio.hyperfoil.controller.port=8090",
                        f"-Dio.hyperfoil.distdir={distribution}",
                        "-cp",
                        classpath,
                        "io.hyperfoil.Hyperfoil$Standalone",
                    ],
                    cwd=output,
                )
                wait_ready(LOAD_URL + "/version", processes)
                source = yaml.safe_load(
                    (artifacts / "upstream-workload.yaml").read_text()
                )
                configuration = yaml.safe_dump(
                    workload(source, rate, warmup, duration), sort_keys=False
                )
                (output / "workload.yaml").write_text(configuration, encoding="utf-8")
                http(
                    LOAD_URL + "/benchmark",
                    body=configuration.encode(),
                    content_type="text/vnd.yaml",
                )
                samples = [snapshot(processes)]
                run_info = json.loads(
                    http(LOAD_URL + "/benchmark/quarkus-activemq/start")[1]
                )
                run_url = LOAD_URL + "/run/" + run_info["id"]
                deadline = time.monotonic() + warmup + duration + 120
                with (output / "process-samples.jsonl").open(
                    "w", encoding="utf-8"
                ) as stream:
                    while True:
                        processes.check()
                        sample = snapshot(processes)
                        samples.append(sample)
                        stream.write(json.dumps(sample) + "\n")
                        stream.flush()
                        run_info = get_json(run_url)
                        if run_info["terminated"] is not None:
                            break
                        if time.monotonic() >= deadline:
                            raise TimeoutError(
                                "Hyperfoil did not finish within its deadline"
                            )
                        time.sleep(0.5)
                save(output / "run.json", run_info)
                result = persisted_result(output, run_info["id"], processes)
                save(output / "hyperfoil.json", result)
                metrics = analyze(result, samples, cpus, rate, duration)
                if (
                    run_info["cancelled"]
                    or not run_info["completed"]
                    or run_info["errors"]
                ):
                    metrics["errors"].append(f"Hyperfoil failed: {run_info['errors']}")
                    metrics["valid"] = False
                time.sleep(3)
                stats = get_json(TELEMETRY_URL + "/stats")
                save(output / "telemetry.json", stats)
                validate_spans(stats, agent)
                server_spans = stats["kinds"].get("2", 0)
                all_requests = sum(
                    entry["total"]["summary"]["requestCount"]
                    for entry in result["stats"]
                )
                if agent != "no-agent":
                    expected = 0.1 * all_requests
                    if abs(server_spans - expected) > max(20, 6 * math.sqrt(expected)):
                        metrics["errors"].append(
                            "Exported server span count does not match 10% sampling"
                        )
                        metrics["valid"] = False
                log = (output / "app.log").read_text(encoding="utf-8")
                if "Failed to export" in log or "Unable to export" in log:
                    metrics["errors"].append("The agent reported export failures")
                    metrics["valid"] = False
                database_count = run(
                    [
                        PG_BIN / "psql",
                        "-h",
                        "127.0.0.1",
                        "-p",
                        "15432",
                        "-U",
                        "pguser",
                        "-d",
                        "test",
                        "--no-align",
                        "--tuples-only",
                        "-c",
                        "SELECT count(*) FROM legume;",
                    ],
                    capture_output=True,
                    text=True,
                ).stdout
                (output / "database-count.txt").write_text(
                    database_count, encoding="utf-8"
                )
                metrics["database_rows"] = int(database_count.strip())
                if metrics["database_rows"] != metrics["expected_database_rows"]:
                    metrics["errors"].append(
                        "Database row count does not match successful inserts"
                    )
                    metrics["valid"] = False
                save(output / "metrics.json", metrics)
                print(
                    f"Finished {output.name}: {metrics['cpu_us_per_request']:.3f} CPU us/request, valid={metrics['valid']}",
                    flush=True,
                )
                return metrics
    finally:
        active_agent.unlink(missing_ok=True)


def summarize(rows, same_agent, mode="measure"):
    lines = [
        "# Quarkus database benchmark",
        "",
        "A/A comparison with byte-identical agents."
        if same_agent
        else "Baseline/candidate comparison.",
        "",
        (
            "Steady-state measurement trials."
            if mode == "measure"
            else f"{mode.capitalize()} diagnostics, not a steady-state performance comparison."
        ),
        "",
        "Application CPU time per successful request includes SDK processing and OTLP export.",
        "PostgreSQL, the load generator, and the non-persisting OTLP receiver have separate CPU sets.",
        "Fresh process/database trials are observations; requests are not independent replicates.",
        "",
        "| Agent | Requests/s | Valid/total trials | CPU us/request median [min, max] |",
        "| --- | --- | --- | --- |",
    ]
    for rate, agent in sorted({(row["rate"], row["agent"]) for row in rows}):
        group = [row for row in rows if row["rate"] == rate and row["agent"] == agent]
        values = [row["cpu_us_per_request"] for row in group if row["valid"]]
        cell = (
            f"{statistics.median(values):.3f} [{min(values):.3f}, {max(values):.3f}]"
            if values
            else "invalid"
        )
        lines.append(f"| {agent} | {rate} | {len(values)}/{len(group)} | {cell} |")
    lines.extend(
        [
            "",
            "Invalid trials are retained and must not be used as overhead comparisons.",
            "",
        ]
    )
    for row in rows:
        if not row["valid"]:
            lines.append(f"- {row['name']}: {'; '.join(row['errors'])}")
    for rate in sorted({row["rate"] for row in rows}):
        baseline = {
            row["repetition"]: row
            for row in rows
            if row["rate"] == rate and row["agent"] == "baseline" and row["valid"]
        }
        candidate = {
            row["repetition"]: row
            for row in rows
            if row["rate"] == rate and row["agent"] == "candidate" and row["valid"]
        }
        if baseline and baseline.keys() == candidate.keys():
            deltas = [
                100
                * (
                    candidate[index]["cpu_us_per_request"]
                    / before["cpu_us_per_request"]
                    - 1
                )
                for index, before in baseline.items()
            ]
            lines.extend(
                [
                    "",
                    f"Paired candidate CPU deltas at {rate} requests/s: "
                    + ", ".join(f"{value:+.2f}%" for value in deltas)
                    + ".",
                ]
            )
    lines.extend(
        [
            "",
            "No observations are retried or discarded. Confirm changes in an independent job.",
            "",
        ]
    )
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--artifacts", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument(
        "--mode", choices=("smoke", "calibrate", "measure", "profile"), required=True
    )
    parser.add_argument("--rate", type=int, default=2000)
    parser.add_argument("--repetitions", type=int, default=5)
    args = parser.parse_args()
    if not 1 <= args.rate <= 20000 or not 1 <= args.repetitions <= 10:
        raise ValueError("Rate must be 1..20000 and repetitions 1..10")
    for name in os.environ:
        if name.startswith("OTEL_") or name in (
            "JAVA_TOOL_OPTIONS",
            "_JAVA_OPTIONS",
            "JDK_JAVA_OPTIONS",
        ):
            raise ValueError(f"Remove inherited benchmark configuration: {name}")
    artifacts = args.artifacts.resolve(strict=True)
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    java = shutil.which("java")
    if java is None:
        raise ValueError("java must be on PATH")
    topology = run(
        ["lscpu", "--parse=CPU,CORE,SOCKET,NODE,ONLINE"], capture_output=True, text=True
    ).stdout
    cpus = select_cpus(topology, os.sched_getaffinity(0))
    hashes = {}
    for path in sorted(artifacts.rglob("*")):
        if path.is_file() and "hyperfoil" not in path.relative_to(artifacts).parts[:-1]:
            with path.open("rb") as stream:
                hashes[str(path.relative_to(artifacts))] = hashlib.file_digest(
                    stream, "sha256"
                ).hexdigest()
    for line in (artifacts / "SHA256SUMS").read_text(encoding="utf-8").splitlines():
        expected, name = line.split(maxsplit=1)
        name = name.removeprefix("artifacts/")
        if hashes.get(name) != expected:
            raise ValueError(f"Artifact differs from the hosted build: {name}")
    metadata = {
        "provenance": json.loads((artifacts / "provenance.json").read_text()),
        "workflow_run": os.environ.get("GITHUB_RUN_ID"),
        "harness_commit": os.environ.get("GITHUB_SHA"),
        "platform": platform.platform(),
        "java": run([java, "-version"], capture_output=True, text=True).stderr,
        "postgres": run(
            [PG_BIN / "postgres", "--version"], capture_output=True, text=True
        ).stdout,
        "python": sys.version,
        "python_dependencies": run(
            [sys.executable, "-m", "pip", "freeze"], capture_output=True, text=True
        ).stdout,
        "cpu_info": run(["lscpu"], capture_output=True, text=True).stdout,
        "cpu_topology": topology,
        "cpus": cpus,
        "mode": args.mode,
        "hashes": hashes,
        "driver_sha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        "receiver_sha256": hashlib.sha256(
            (HERE / "telemetry_receiver.py").read_bytes()
        ).hexdigest(),
    }
    save(output / "metadata.json", metadata)
    os.sched_setaffinity(0, cpus["driver"])
    repetitions = args.repetitions if args.mode == "measure" else 1
    warmup, duration = {
        "smoke": (5, 10),
        "calibrate": (30, 60),
        "measure": (300, 300),
        "profile": (300, 120),
    }[args.mode]
    rates = [500, 2000, 5000, 10000] if args.mode == "calibrate" else [args.rate]
    rows = []
    for rate in rates:
        for repetition, agent in schedule(repetitions):
            if args.mode == "calibrate" and agent == "candidate":
                continue
            name = f"{rate}-{repetition}-{agent}"
            metrics = execute(
                artifacts,
                output / name,
                cpus,
                java,
                agent,
                rate,
                warmup,
                duration,
                args.mode,
            )
            rows.append(
                {
                    "name": name,
                    "rate": rate,
                    "repetition": repetition,
                    "agent": agent,
                    **metrics,
                }
            )
            save(output / "trials.json", rows)
            (output / "summary.md").write_text(
                summarize(
                    rows,
                    hashes["baseline-agent.jar"] == hashes["candidate-agent.jar"],
                    args.mode,
                ),
                encoding="utf-8",
            )
            if not metrics["valid"] and args.mode != "calibrate":
                raise ValueError(
                    f"Invalid comparison trial: {name}: {metrics['errors']}"
                )


if __name__ == "__main__":
    main()
