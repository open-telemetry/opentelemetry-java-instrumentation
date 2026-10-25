# Copyright The OpenTelemetry Authors
# SPDX-License-Identifier: Apache-2.0

"""Compare two agent artifacts using one servlet JMH artifact."""

import argparse
import csv
import hashlib
import json
import math
import os
import platform
import shlex
import shutil
import statistics
import subprocess
from pathlib import Path

PACKAGE = "io.opentelemetry.javaagent.benchmark.servlet."
VARIANTS = {
    "sampled": "ServletBenchmark",
    "one-percent": "ServletWithOnePercentSamplingBenchmark",
    "unsampled": "ServletWithNoSamplingBenchmark",
}
JVM_ARGS = [
    "-Xms1g",
    "-Xmx1g",
    "-XX:+UseG1GC",
    "-XX:ActiveProcessorCount=4",
    "-Dotel.traces.exporter=none",
    "-Dotel.metrics.exporter=none",
    "-Dotel.logs.exporter=none",
    "-Dotel.instrumentation.http-url-connection.enabled=false",
]


def run(command, **kwargs):
    return subprocess.run(
        [str(arg) for arg in command],
        check=True,
        creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0,
        **kwargs,
    )


def select_cpus(topology, allowed):
    nodes = {}
    for line in topology.splitlines():
        if not line or line.startswith("#"):
            continue
        cpu, core, socket, node, online = line.split(",")
        if online != "Y" or int(cpu) not in allowed:
            continue
        nodes.setdefault((socket, node), {}).setdefault(core, int(cpu))
    for node in sorted(nodes):
        cpus = list(nodes[node].values())
        if len(cpus) >= 4:
            return sorted(cpus)[:4]
    raise ValueError("Four allowed physical cores on one NUMA node are required")


def schedule(repetitions, variants):
    for repetition in range(repetitions):
        order = (
            ("baseline", "candidate")
            if repetition % 2 == 0
            else ("candidate", "baseline")
        )
        yield repetition, "no-agent", "sampled"
        for variant in variants:
            for agent in order:
                yield repetition, agent, variant


def command(
    java, benchmark, agent, variant, output, mode, preflight=False, extension=None
):
    args = list(JVM_ARGS)
    if agent is not None:
        args.append(f"-javaagent:{agent}")
        if extension is None:
            raise ValueError("A benchmark span processor extension is required")
        args.extend(
            [
                f"-Dotel.javaagent.extensions={extension}",
                "-Dbenchmark.verify-tracing=true",
            ]
        )
    if preflight:
        args.remove("-Dotel.traces.exporter=none")
        args.append("-Dotel.traces.exporter=logging")
    if mode == "profile":
        args.append(
            "-XX:StartFlightRecording=settings=profile,dumponexit=true,"
            f"filename={output.with_suffix('.jfr')}"
        )
        args.extend(["-XX:+UnlockDiagnosticVMOptions", "-XX:+DebugNonSafepoints"])
    smoke = mode == "smoke" or preflight
    return [
        java,
        "-jar",
        benchmark,
        f"^{PACKAGE}{VARIANTS[variant]}.execute$",
        "-f",
        "1",
        "-t",
        "1",
        "-wi",
        "0" if smoke else "5",
        "-i",
        "1" if smoke else "5",
        "-w",
        "5s",
        "-r",
        "100ms" if smoke else "5s",
        "-foe",
        "true",
        "-prof",
        "gc",
        "-rf",
        "json",
        "-rff",
        output,
        "-jvmArgsPrepend",
        subprocess.list2cmdline(args) if os.name == "nt" else shlex.join(args),
    ]


def read_result(path, expected_variant):
    results = json.loads(path.read_text(encoding="utf-8"))
    if len(results) != 1:
        raise ValueError(f"Expected one benchmark in {path}")
    result = results[0]
    if result["benchmark"] != f"{PACKAGE}{VARIANTS[expected_variant]}.execute":
        raise ValueError(f"Unexpected benchmark in {path}")
    primary = result["primaryMetric"]
    allocation = result["secondaryMetrics"]["gc.alloc.rate.norm"]
    values = [
        primary["score"],
        allocation["score"],
        *primary["scorePercentiles"].values(),
    ]
    if (
        result["mode"] != "sample"
        or result["forks"] != 1
        or result["threads"] != 1
        or primary["scoreUnit"] != "us/op"
        or allocation["scoreUnit"] != "B/op"
        or not all(math.isfinite(value) and value >= 0 for value in values)
    ):
        raise ValueError(f"Invalid JMH result in {path}")
    secondary = result["secondaryMetrics"]
    return {
        "us_per_op": primary["score"],
        "p95_us": primary["scorePercentiles"]["95.0"],
        "bytes_per_op": allocation["score"],
        "gc_count": secondary["gc.count"]["score"],
        "gc_ms": secondary.get("gc.time", {}).get("score", 0),
    }


def summarize(rows, same_agent):
    lines = [
        "# Servlet benchmark comparison",
        "",
        "A/A comparison: agent artifacts are identical."
        if same_agent
        else "Baseline/candidate comparison.",
        "",
        "Each observation is a fresh JVM fork. Values are fork medians [min, max].",
        "Timing includes the HTTP client and server. Exporters are disabled.",
        "Allocation is JVM-wide, including the client. It is not sampled independently per request.",
        "",
        "| Agent | Sampling | Mean us/op | p95 us | Allocated B/op |",
        "| --- | --- | --- | --- | --- |",
    ]
    for agent, variant in sorted({(row["agent"], row["variant"]) for row in rows}):
        group = [
            row for row in rows if row["agent"] == agent and row["variant"] == variant
        ]
        cells = []
        for metric in ("us_per_op", "p95_us", "bytes_per_op"):
            values = [row[metric] for row in group]
            cells.append(
                f"{statistics.median(values):.3f} [{min(values):.3f}, {max(values):.3f}]"
            )
        lines.append(f"| {agent} | {variant} | " + " | ".join(cells) + " |")
    lines.extend(
        [
            "",
            "| Sampling | Candidate time delta | Candidate allocation delta | Paired time deltas |",
            "| --- | --- | --- | --- |",
        ]
    )
    for variant in VARIANTS:
        baseline = {
            r["repetition"]: r
            for r in rows
            if r["agent"] == "baseline" and r["variant"] == variant
        }
        candidate = {
            r["repetition"]: r
            for r in rows
            if r["agent"] == "candidate" and r["variant"] == variant
        }
        if not baseline:
            continue
        if baseline.keys() != candidate.keys():
            raise ValueError("Incomplete baseline/candidate pairs")
        deltas = []
        for metric in ("us_per_op", "bytes_per_op"):
            before = statistics.median(r[metric] for r in baseline.values())
            after = statistics.median(r[metric] for r in candidate.values())
            deltas.append(f"{100 * (after / before - 1):+.2f}%")
        pairs = [
            f"{100 * (candidate[i]['us_per_op'] / baseline[i]['us_per_op'] - 1):+.2f}%"
            for i in baseline
        ]
        lines.append(f"| {variant} | {' | '.join(deltas)} | {', '.join(pairs)} |")
    control = statistics.median(
        r["us_per_op"] for r in rows if r["agent"] == "no-agent"
    )
    lines.extend(
        [
            "",
            "| Agent | Sampling | Time above no-agent | Overhead |",
            "| --- | --- | --- | --- |",
        ]
    )
    for agent in ("baseline", "candidate"):
        for variant in VARIANTS:
            values = [
                r["us_per_op"]
                for r in rows
                if r["agent"] == agent and r["variant"] == variant
            ]
            if values:
                value = statistics.median(values)
                lines.append(
                    f"| {agent} | {variant} | {value - control:+.3f} us/op | {100 * (value / control - 1):+.2f}% |"
                )
    lines.extend(
        [
            "",
            "Negative candidate deltas mean lower cost. No outliers are removed or retried.",
            "These exploratory differences are not significance tests or proof of an improvement.",
            "Use A/A spread, alternating order, and an independent confirmation run before accepting a change.",
            "",
        ]
    )
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--benchmark-jar", type=Path, required=True)
    parser.add_argument("--extension-jar", type=Path, required=True)
    parser.add_argument("--baseline-agent", type=Path, required=True)
    parser.add_argument("--candidate-agent", type=Path, required=True)
    parser.add_argument("--provenance", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument(
        "--mode", choices=("smoke", "measure", "profile"), default="measure"
    )
    args = parser.parse_args()
    for name in os.environ:
        if name.startswith("OTEL_") or name in (
            "JAVA_TOOL_OPTIONS",
            "_JAVA_OPTIONS",
            "JDK_JAVA_OPTIONS",
        ):
            raise ValueError(f"Remove inherited benchmark configuration: {name}")
    artifacts = {
        "benchmark": args.benchmark_jar.resolve(strict=True),
        "extension": args.extension_jar.resolve(strict=True),
        "baseline": args.baseline_agent.resolve(strict=True),
        "candidate": args.candidate_agent.resolve(strict=True),
    }
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    java = shutil.which("java")
    if java is None:
        raise ValueError("java must be on PATH")
    topology = run(
        ["lscpu", "--parse=CPU,CORE,SOCKET,NODE,ONLINE"], capture_output=True, text=True
    ).stdout
    cpus = select_cpus(topology, os.sched_getaffinity(0))
    os.sched_setaffinity(0, cpus)
    hashes = {}
    for name, path in artifacts.items():
        with path.open("rb") as stream:
            hashes[name] = hashlib.file_digest(stream, "sha256").hexdigest()
    metadata = {
        "provenance": json.loads(args.provenance.read_text(encoding="utf-8")),
        "driver_sha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        "workflow_run": os.environ.get("GITHUB_RUN_ID"),
        "platform": platform.platform(),
        "cpu_info": run(["lscpu"], capture_output=True, text=True).stdout,
        "cpu_topology": topology,
        "cpus": cpus,
        "java": run([java, "-version"], capture_output=True, text=True).stderr,
        "artifacts": {
            name: {"path": str(path), "sha256": hashes[name]}
            for name, path in artifacts.items()
        },
        "mode": args.mode,
        "commands": [],
    }
    metadata_path = output / "metadata.json"

    def execute(name, agent, variant, mode, preflight=False):
        result_path = output / f"{name}.json"
        active_agent = output / "active-agent.jar"
        if agent is not None:
            shutil.copyfile(agent, active_agent)
        cmd = command(
            java,
            artifacts["benchmark"],
            active_agent if agent is not None else None,
            variant,
            result_path,
            mode,
            preflight,
            artifacts["extension"],
        )
        metadata["commands"].append([str(arg) for arg in cmd])
        metadata_path.write_text(
            json.dumps(metadata, indent=2) + "\n", encoding="utf-8"
        )
        print(name, flush=True)
        try:
            with (output / f"{name}.log").open("w", encoding="utf-8") as log:
                run(cmd, stdout=log, stderr=subprocess.STDOUT)
        finally:
            active_agent.unlink(missing_ok=True)
        if agent is not None and "Verified active tracing and sampler " not in (
            output / f"{name}.log"
        ).read_text(encoding="utf-8"):
            raise ValueError(f"SDK tracing was not verified in {name}.log")
        return read_result(result_path, variant)

    for agent in ("baseline", "candidate"):
        execute(
            f"preflight-{agent}", artifacts[agent], "sampled", "smoke", preflight=True
        )
        log = (output / f"preflight-{agent}.log").read_text(encoding="utf-8")
        if " SERVER " not in log or "'GET /'" not in log:
            raise ValueError(
                f"No exported servlet server span in preflight-{agent}.log"
            )

    repetitions = 3 if args.mode == "measure" else 1
    variants = VARIANTS if args.mode != "profile" else ("sampled",)
    rows = []
    for repetition, agent, variant in schedule(repetitions, variants):
        metrics = execute(
            f"{repetition}-{agent}-{variant}",
            None if agent == "no-agent" else artifacts[agent],
            variant,
            args.mode,
        )
        rows.append(
            {"repetition": repetition, "agent": agent, "variant": variant, **metrics}
        )
        with (output / "forks.csv").open("w", encoding="utf-8", newline="") as stream:
            writer = csv.DictWriter(stream, fieldnames=list(rows[0]))
            writer.writeheader()
            writer.writerows(rows)
    if args.mode == "measure":
        (output / "summary.md").write_text(
            summarize(rows, hashes["baseline"] == hashes["candidate"]), encoding="utf-8"
        )
    else:
        (output / "summary.md").write_text(
            f"# Servlet {args.mode} run\n\nNot a timing comparison. See raw results and logs.\n",
            encoding="utf-8",
        )


if __name__ == "__main__":
    main()
