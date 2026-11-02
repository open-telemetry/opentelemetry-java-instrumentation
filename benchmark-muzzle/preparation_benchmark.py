#!/usr/bin/env python3
"""Measure conditional Muzzle disk preparation on fresh production runner images."""

import argparse
import json
import os
from pathlib import Path
import shutil
import sys
import time

import benchmark
import ci_benchmark as ci


MINIMUM_BYTES = 50 * 1024**3
ROOT = Path(__file__).resolve().parents[1]
CLEANUP = ROOT / ".github/scripts/gha-free-disk-space.sh"
GUARD = ROOT / ".github/scripts/gha-muzzle-free-disk-space.sh"


def measure(args):
    if (
        sys.platform != "linux" or os.environ.get("GITHUB_ACTIONS") != "true"
        or os.environ.get("RUNNER_ENVIRONMENT") != "github-hosted"
    ):
        raise ValueError("Preparation measurements run only on fresh GitHub-hosted Linux CI runners")
    args.output.mkdir(parents=True, exist_ok=False)
    initial = shutil.disk_usage(ROOT).free
    if initial < MINIMUM_BYTES:
        raise ValueError("The runner lacks headroom to benchmark the cleanup skip path")
    samples = []
    result = {
        "complete": False, "round": args.round, "initial_free_bytes": initial,
        "threshold_bytes": MINIMUM_BYTES, "image": os.environ.get("ImageVersion"),
        "revision": benchmark.run(["git", "rev-parse", "HEAD"]).decode().strip(),
        "cleanup_sha256": benchmark.digest(CLEANUP), "guard_sha256": benchmark.digest(GUARD),
        "samples": samples,
    }
    ci.write_json(args.output / "preparation.json", result)
    order = ("candidate", "baseline") if args.round % 2 else ("baseline", "candidate")
    for label in order:
        log = args.output / f"{label}.log"
        sample = {"label": label, "free_before_bytes": shutil.disk_usage(ROOT).free, "success": False}
        samples.append(sample)
        started = time.perf_counter()
        try:
            benchmark.run(["bash", "-e", str(GUARD if label == "candidate" else CLEANUP)], cwd=ROOT, log=log)
            sample["success"] = True
        finally:
            sample["duration_ms"] = (time.perf_counter() - started) * 1000
            sample["free_after_bytes"] = shutil.disk_usage(ROOT).free
            ci.write_json(args.output / "preparation.json", result)
        if label == "candidate" and "MUZZLE_DISK_PREPARATION decision=skip " not in log.read_text(encoding="utf-8"):
            raise ValueError("The candidate did not take the expected disk-preparation skip path")
    result["complete"] = True
    ci.write_json(args.output / "preparation.json", result)


def report(args):
    rows = []
    reference = None
    for number in range(1, args.rounds + 1):
        path = ci.artifact_path(
            args.inputs, f"muzzle-preparation-{number}", "preparation.json", single=args.rounds == 1,
        )
        value = json.loads(path.read_text(encoding="utf-8"))
        common = {key: value[key] for key in ("revision", "cleanup_sha256", "guard_sha256", "threshold_bytes")}
        if reference is None:
            reference = common
        benchmark.require_equal(reference, common, "Preparation benchmark source and policy")
        expected_order = ["candidate", "baseline"] if number % 2 else ["baseline", "candidate"]
        if (
            not value["complete"] or value["round"] != number
            or value["initial_free_bytes"] < MINIMUM_BYTES
            or value["threshold_bytes"] != MINIMUM_BYTES
            or [sample["label"] for sample in value["samples"]] != expected_order
            or not all(sample["success"] for sample in value["samples"])
        ):
            raise ValueError("Incomplete or invalid preparation pair")
        if "MUZZLE_DISK_PREPARATION decision=skip " not in (path.parent / "candidate.log").read_text(encoding="utf-8"):
            raise ValueError("The measured guard did not skip cleanup")
        durations = {sample["label"]: sample["duration_ms"] for sample in value["samples"]}
        rows.append(dict(value, savings_ms=durations["baseline"] - durations["candidate"]))
    args.output.mkdir(parents=True, exist_ok=False)
    result = {
        "complete": True, "rounds": rows,
        "paired_savings_ms": ci.spread([row["savings_ms"] for row in rows]),
        "scope": "Disk-preparation step only, not a Gradle command or whole-PR timing comparison.",
    }
    ci.write_json(args.output / "results.json", result)
    lines = [
        "# Conditional Muzzle disk preparation", "",
        "Each round uses a fresh production runner image. Both scripts run on the same runner, "
        "alternating order across rounds. The guard skips cleanup before and after the baseline "
        "because both free-space values exceed 50 GiB. Cleanup removes preinstalled SDKs and "
        "unused container images, not repository files or Gradle caches.", "",
        "| Round | Initial free space | Unconditional cleanup | Guard | Saved |",
        "| --- | ---: | ---: | ---: | ---: |",
    ]
    for row in rows:
        values = {sample["label"]: sample["duration_ms"] / 1000 for sample in row["samples"]}
        lines.append(
            f"| {row['round']} | {row['initial_free_bytes'] / 2**30:.1f} GiB "
            f"| {values['baseline']:.3f}s | {values['candidate']:.3f}s | {row['savings_ms'] / 1000:.3f}s |"
        )
    lines += [
        "", f"Median paired preparation savings: {result['paired_savings_ms']['median'] / 1000:.3f}s.",
        "", "This directly measures the changed preparation step. It does not claim a change in "
        "Gradle execution time or total PR completion time. Lower-disk runners retain the existing "
        "cleanup path. Full Muzzle workload and disk-headroom validation are separate from these pairs.",
    ]
    (args.output / "summary.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    run = commands.add_parser("run")
    run.add_argument("--round", type=int, choices=range(1, 11), required=True)
    run.add_argument("--output", type=Path, required=True)
    summary = commands.add_parser("report")
    summary.add_argument("--inputs", type=Path, required=True)
    summary.add_argument("--rounds", type=int, choices=range(1, 11), required=True)
    summary.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if args.command == "report":
        report(args)
    else:
        measure(args)


if __name__ == "__main__":
    main()
