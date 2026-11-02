#!/usr/bin/env python3
"""Compare four and eight Muzzle slices with identical compatibility coverage."""

import argparse
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tarfile
from types import SimpleNamespace

import benchmark
import ci_benchmark as ci


ROLES = ("baseline", "control", "candidate-a", "candidate-b")


def plan(round_number, partition):
    roles = ROLES if round_number % 2 else tuple(reversed(ROLES))
    return [
        {
            "role": role, "slices": 8 if role.startswith("candidate") else 4,
            "partition": partition + (4 if role == "candidate-b" else 0),
            "label": "candidate" if role.startswith("candidate") else role,
        }
        for role in roles
    ]


def measure(args):
    source = args.repository.resolve()
    root = args.work_dir.resolve()
    if root.exists() or root.is_relative_to(source) or source.is_relative_to(root):
        raise ValueError("The measurement work directory must be new and outside the source checkout")
    root.mkdir(parents=True)
    reports = root / "reports"
    reports.mkdir()
    revision = benchmark.run(["git", "-C", str(source), "rev-parse", "--verify", "--end-of-options", f"{args.revision}^{{commit}}"]).decode().strip()
    script = benchmark.run(["git", "-C", str(source), "show", f"{revision}:instrumentation/build.gradle.kts"]).decode()
    if 'gradleProperty("muzzleSlices")' not in script:
        raise ValueError("The source revision does not support configurable Muzzle slices")
    ci.write_json(reports / "identity.json", {
        "revision": revision, "partition": args.partition, "round": args.round,
        "cache_snapshot_sha256": args.snapshot_sha256, "experiment": "slice-count",
        "plan": plan(args.round, args.partition),
    })
    try:
        for command in plan(args.round, args.partition):
            role = command["role"]
            runner = ci.Comparison(SimpleNamespace(
                repository=args.repository, work_dir=root / role,
                baseline=revision, candidate=revision, partition=command["partition"],
                round=args.round, cache_snapshot=args.cache_snapshot,
                snapshot_sha256=args.snapshot_sha256, muzzle_slices=command["slices"],
            ))
            try:
                runner.execute(command["label"])
                runner.persist(complete=True)
            finally:
                shutil.move(runner.reports, reports / role)
            shutil.rmtree(root / role)
        validate_group(reports)
        ci.write_json(reports / "complete.json", {"complete": True})
    except (OSError, ValueError, tarfile.TarError, subprocess.CalledProcessError) as error:
        (reports / "failure.txt").write_text(str(error) + "\n", encoding="utf-8")
        raise


def validate_group(directory):
    identity = json.loads((directory / "identity.json").read_text(encoding="utf-8"))
    expected_plan = plan(identity["round"], identity["partition"])
    benchmark.require_equal(expected_plan, identity["plan"], "Slice-count command plan")
    shared = None
    manifests = {}
    samples = {}
    discoveries = {}
    for command in expected_plan:
        role = command["role"]
        report = directory / role
        child = json.loads((report / "identity.json").read_text(encoding="utf-8"))
        raw = json.loads((report / "samples.json").read_text(encoding="utf-8"))
        if (
            not raw["complete"] or len(raw["samples"]) != 1
            or child["revisions"] != dict.fromkeys(("baseline", "candidate"), identity["revision"])
            or child["muzzle_slices"] != command["slices"]
            or child["partition"] != command["partition"] or child["round"] != identity["round"]
            or child["cache_snapshot_sha256"] != identity["cache_snapshot_sha256"]
        ):
            raise ValueError("Incomplete or misidentified slice-count command")
        common = {key: value for key, value in child.items() if key not in {"partition", "muzzle_slices", "platform"}}
        if shared is None:
            shared = common
        benchmark.require_equal(shared, common, "Slice-count source, runtime and cache conditions")
        sample = raw["samples"][0]
        if not sample["process_success"] or sample["label"] != command["label"] or sample["revision"] != identity["revision"]:
            raise ValueError("Failed or misidentified slice-count sample")
        output = report / command["label"]
        actual = benchmark.manifests(output / "assertions")
        log = (output / "console.log").read_text(encoding="utf-8")
        invocation = json.loads((output / "command.json").read_text(encoding="utf-8"))
        if (
            f"-PmuzzleSlices={command['slices']}" not in invocation
            or f":instrumentation:muzzle{command['partition']}" not in invocation
            or sample["assertions"] != len(actual)
        ):
            raise ValueError("The recorded command does not select the expected slice")
        benchmark.require_equal(sample["task_outcomes"], ci.validate_build(log, actual), "Recorded task outcomes")
        ci.validate_diagnostics(output, sample)
        manifests[role] = actual
        samples[role] = sample
        discoveries[role] = sample["discovery"]["projects"]
    benchmark.require_equal(manifests["baseline"], manifests["control"], "Identical-four-slice control workload")
    if manifests["candidate-a"].keys() & manifests["candidate-b"].keys():
        raise ValueError("Eight-slice workloads overlap")
    benchmark.require_equal(
        manifests["baseline"], manifests["candidate-a"] | manifests["candidate-b"],
        "Combined eight-slice compatibility workload",
    )
    benchmark.require_equal(samples["baseline"]["task_outcomes"], samples["control"]["task_outcomes"], "Four-slice task-cache control")
    combined_tasks = dict(samples["candidate-a"]["task_outcomes"])
    for task, outcome in samples["candidate-b"]["task_outcomes"].items():
        if task in combined_tasks and combined_tasks[task] != outcome:
            raise ValueError("Shared eight-slice tasks have different cache outcomes")
        combined_tasks[task] = outcome
    benchmark.require_equal(
        {task: outcome for task, outcome in samples["baseline"]["task_outcomes"].items() if not re.fullmatch(r":instrumentation:muzzle[1-8]", task)},
        {task: outcome for task, outcome in combined_tasks.items() if not re.fullmatch(r":instrumentation:muzzle[1-8]", task)},
        "Combined eight-slice task-cache outcomes",
    )
    benchmark.require_equal(discoveries["baseline"], discoveries["control"], "Four-slice discovery control")
    if discoveries["candidate-a"].keys() & discoveries["candidate-b"].keys():
        raise ValueError("Eight-slice discovery projects overlap")
    benchmark.require_equal(discoveries["baseline"], discoveries["candidate-a"] | discoveries["candidate-b"], "Combined discovery coverage")
    return identity, shared, manifests, samples


def aggregate(args):
    maxima = []
    reference = {}
    conditions = None
    groups = []
    for number in range(1, args.rounds + 1):
        values = {label: [] for label in ci.LABELS}
        for partition in args.partitions:
            directory = ci.artifact_path(
                args.inputs, f"muzzle-slices-{number}-{partition}", "identity.json",
                single=args.rounds == 1 and len(args.partitions) == 1,
            ).parent
            if not json.loads((directory / "complete.json").read_text(encoding="utf-8"))["complete"]:
                raise ValueError("Incomplete slice-count group")
            identity, shared, manifests, samples = validate_group(directory)
            if identity["round"] != number or identity["partition"] != partition:
                raise ValueError("Misidentified slice-count group")
            common = {key: value for key, value in shared.items() if key != "round"}
            if conditions is None:
                conditions = common
            benchmark.require_equal(conditions, common, "Cross-round slice-count conditions")
            if partition not in reference:
                reference[partition] = manifests
            benchmark.require_equal(reference[partition], manifests, "Cross-round slice-count workload")
            values["baseline"].append(samples["baseline"]["command_ms"])
            values["control"].append(samples["control"]["command_ms"])
            values["candidate"].extend(samples[role]["command_ms"] for role in ("candidate-a", "candidate-b"))
            groups.append({"round": number, "partition": partition, "samples": samples})
        row = [{"label": label, "command_ms": max(durations)} for label, durations in values.items()]
        maxima.append({"round": number, "samples": row, "paired": ci.paired_values(row, "command_ms")})
    args.output.mkdir(parents=True, exist_ok=False)
    result = {
        "complete": True, "conditions": conditions, "rounds": args.rounds,
        "partitions": args.partitions, "maximum_slice_rounds": maxima, "groups": groups,
        "maximum_slice_paired": {
            label: {
                field: ci.spread([row["paired"][label][field] for row in maxima])
                for field in ("delta_ms", "percent")
            } for label in ("candidate", "control")
        },
    }
    ci.write_json(args.output / "results.json", result)
    lines = [
        "# Four versus eight Muzzle slices", "",
        "The source, compiled build logic and immutable cache snapshot are identical. "
        "Every command has a fresh checkout, independent Gradle home, live version metadata, "
        "read-only remote cache and executed assertions. The union of the eight slices must "
        "match the four-slice compatibility workload exactly, including classpath order and hashes.", "",
        "Each round/group measures a four-slice baseline, identical control and the corresponding "
        "two eight-slice commands on one runner, reversing order in even rounds. "
        "The primary estimator takes the maximum of four baseline or eight candidate commands "
        "within each round. It is not observed Actions fan-out completion or whole-PR wall time. "
        "Setup and cache transfer/extraction costs are excluded from this command metric.", "",
        "| Round | Four-slice maximum | Identical control | Eight-slice maximum |",
        "| --- | ---: | ---: | ---: |",
    ]
    for row in maxima:
        times = {sample["label"]: sample["command_ms"] / 1000 for sample in row["samples"]}
        lines.append(f"| {row['round']} | {times['baseline']:.2f}s | {times['control']:.2f}s | {times['candidate']:.2f}s |")
    for label in ("candidate", "control"):
        paired = result["maximum_slice_paired"][label]
        lines += ["", f"{label.capitalize()} median paired change: {paired['delta_ms']['median'] / 1000:+.2f}s, {paired['percent']['median']:+.2f}%."]
    lines += [
        "", "Eight slices use more runners and repeat startup, build configuration and shared prerequisites. "
        "This experiment does not increase persistent cache storage or reduce tested versions. "
        "No failed measurements or outliers are retried or removed.",
    ]
    if args.rounds < 5 or args.partitions != [1, 2, 3, 4]:
        lines += ["", "**Functional run only. Five rounds and all four baseline groups are required for the primary comparison.**"]
    (args.output / "summary.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    run = commands.add_parser("run")
    run.add_argument("--repository", type=Path, default=Path(__file__).resolve().parents[1])
    run.add_argument("--revision", required=True)
    run.add_argument("--round", type=int, choices=range(1, 11), required=True)
    run.add_argument("--partition", type=int, choices=range(1, 5), required=True)
    run.add_argument("--cache-snapshot", type=Path, required=True)
    run.add_argument("--snapshot-sha256", required=True)
    run.add_argument("--work-dir", type=Path, required=True)
    report = commands.add_parser("report")
    report.add_argument("--inputs", type=Path, required=True)
    report.add_argument("--output", type=Path, required=True)
    report.add_argument("--rounds", type=int, choices=range(1, 11), required=True)
    report.add_argument("--partitions", type=int, choices=range(1, 5), nargs="+", required=True)
    args = parser.parse_args()
    if args.command == "report":
        if args.partitions != sorted(set(args.partitions)):
            parser.error("Partitions must be sorted and unique")
        aggregate(args)
    elif sys.platform != "linux":
        parser.error("Measurements run on Linux")
    else:
        measure(args)


if __name__ == "__main__":
    main()
