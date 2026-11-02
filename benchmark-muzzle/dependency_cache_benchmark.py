#!/usr/bin/env python3
"""Prepare a same-revision comparison of accumulated Gradle dependency caches."""

import argparse
import copy
import hashlib
import json
from pathlib import Path, PurePosixPath
import shutil
import subprocess
import sys
import tarfile
import time
from types import SimpleNamespace

import benchmark
import ci_benchmark as ci


def dependency_entry(name):
    path = PurePosixPath(name)
    if path.is_absolute() or ".." in path.parts:
        raise ValueError(f"Unsafe snapshot entry: {name}")
    return path.parts[:2] == ("caches", "modules-2")


class HashingReader:
    def __init__(self, stream):
        self.stream = stream
        self.digest = hashlib.sha256()

    def read(self, size):
        value = self.stream.read(size)
        self.digest.update(value)
        return value


def compose_snapshot(original, warmed, destination):
    """Keep original task/script caches and replace only caches/modules-2."""
    if destination.exists() or destination.resolve() in (original.resolve(), warmed.resolve()):
        raise ValueError("The composed snapshot must be a new file")
    destination.parent.mkdir(parents=True, exist_ok=True)
    inventory = {}
    dependency_bytes = 0
    dependency_files = 0
    with tarfile.open(destination, "w:gz", compresslevel=1) as output:
        for source_path, take_dependencies in ((original, False), (warmed, True)):
            with tarfile.open(source_path, "r:gz") as source:
                for member in source:
                    if dependency_entry(member.name) != take_dependencies:
                        continue
                    info = copy.copy(member)
                    if info.isfile() or info.islnk():
                        with source.extractfile(member) as stream:
                            if info.islnk():
                                info.type = tarfile.REGTYPE
                                info.linkname = ""
                                info.size = stream.seek(0, 2)
                                stream.seek(0)
                            reader = HashingReader(stream)
                            output.addfile(info, reader)
                            if take_dependencies:
                                dependency_files += 1
                                dependency_bytes += info.size
                            else:
                                inventory[info.name] = reader.digest.hexdigest()
                    else:
                        output.addfile(info)
                        if not take_dependencies:
                            inventory[info.name] = {"type": info.type.decode(), "link": info.linkname}
    if not dependency_files:
        raise ValueError("The warmed snapshot contains no Gradle dependency files")
    return {
        "sha256": benchmark.digest(destination),
        "compressed_bytes": destination.stat().st_size,
        "dependency_files": dependency_files,
        "dependency_bytes": dependency_bytes,
        "preserved_non_dependency_manifest_sha256": hashlib.sha256(
            json.dumps(inventory, sort_keys=True).encode()
        ).hexdigest(),
    }


def warm(args):
    source = args.repository.resolve()
    root = args.work_dir.resolve()
    if root.is_relative_to(source) or source.is_relative_to(root):
        raise ValueError("The warming directory must be outside the source checkout")
    if args.work_dir.exists():
        raise ValueError("The warming work directory must be new")
    if args.output.exists() or args.output.resolve().is_relative_to(args.work_dir.resolve()):
        raise ValueError("The snapshot output must be new and outside the warming directory")
    if benchmark.digest(args.cache_snapshot) != args.snapshot_sha256:
        raise ValueError("The initial snapshot SHA-256 does not match")
    args.work_dir.mkdir(parents=True)
    reports = args.work_dir / "reports"
    reports.mkdir()
    initial = args.cache_snapshot.resolve()
    current = initial
    current_digest = args.snapshot_sha256
    workload = json.loads(Path(__file__).with_name("workload.json").read_text(encoding="utf-8"))
    revision = benchmark.run([
        "git", "-C", str(source), "rev-parse", "--verify", "--end-of-options",
        f"{args.revision}^{{commit}}",
    ]).decode().strip()
    stages = []
    warm_tasks = set()
    summary = {
        "complete": False, "revision": revision, "partition": args.partition,
        "measured_seed": workload["seed"], "warm_seeds": [], "stages": stages,
        "initial_snapshot_sha256": current_digest,
        "initial_compressed_bytes": initial.stat().st_size,
        "cache_cleanup": "never; models retaining sampled dependencies between main runs",
        "remote_cache": "read-only", "preserved_cache_entries": "everything outside caches/modules-2",
    }
    ci.write_json(reports / "warming.json", summary)
    try:
        for number in range(1, args.warm_seeds + 1):
            seed = workload["seed"] + number
            runner = ci.Comparison(SimpleNamespace(
                repository=args.repository, work_dir=args.work_dir / f"seed-{seed}",
                baseline=revision, candidate=revision,
                partition=args.partition, round=1, seed=seed,
                cache_snapshot=current, snapshot_sha256=current_digest,
            ))
            try:
                home = runner.execute("candidate", retain_home=True)
            finally:
                moved = reports / f"seed-{seed}"
                shutil.move(runner.reports, moved)
                runner.reports = moved
            runner.persist(complete=True)
            next_snapshot = args.work_dir / f"seed-{seed}.tar.gz"
            started = time.perf_counter()
            next_digest = ci.snapshot(home, next_snapshot)
            stages.append({
                "seed": seed, "snapshot_sha256": next_digest,
                "snapshot_bytes": next_snapshot.stat().st_size,
                "snapshot_ms": (time.perf_counter() - started) * 1000,
                "sample": runner.samples[0],
            })
            summary["warm_seeds"].append(seed)
            warm_tasks.update(benchmark.manifests(reports / f"seed-{seed}" / "candidate" / "assertions"))
            shutil.rmtree(home)
            if current != initial:
                current.unlink()
            current, current_digest = next_snapshot, next_digest
            ci.write_json(reports / "warming.json", summary)
        started = time.perf_counter()
        summary["candidate_snapshot"] = compose_snapshot(initial, current, args.output)
        summary["composition_ms"] = (time.perf_counter() - started) * 1000
        summary["warm_assertion_tasks"] = sorted(warm_tasks)
        current.unlink()
        summary["complete"] = True
        ci.write_json(reports / "warming.json", summary)
        print(json.dumps({key: value for key, value in summary.items() if key != "stages"}, indent=2))
    except (OSError, ValueError, tarfile.TarError, subprocess.CalledProcessError) as error:
        (reports / "failure.txt").write_text(str(error) + "\n", encoding="utf-8")
        raise


def report(args):
    plans = {}
    lines = [
        "", "## Dependency-cache accumulation", "",
        "This experiment models main-only cache writes with `cache-cleanup: never`. "
        "The pinned setup-gradle action's default cleanup removes unused historical versions. "
        "PRs and all benchmark commands keep remote caches read-only. "
        "Only `caches/modules-2` differs between the two snapshot payloads.", "",
        "| Slice | Prior seeds | Original snapshot | Warmed snapshot | Assertion task overlap |",
        "| --- | ---: | ---: | ---: | ---: |",
    ]
    for partition in args.partitions:
        plan = json.loads((args.plans / f"dependency-cache-plan-{partition}" / "warming.json").read_text(encoding="utf-8"))
        identity = json.loads((args.inputs / f"muzzle-ci-1-{partition}" / "identity.json").read_text(encoding="utf-8"))
        if (
            not plan["complete"] or plan["partition"] != partition
            or identity["revisions"]["baseline"] != identity["revisions"]["candidate"]
            or plan["revision"] != identity["revisions"]["baseline"]
            or identity["workload"]["seed"] in plan["warm_seeds"]
            or plan["initial_snapshot_sha256"] != identity["cache_snapshot_sha256"]
            or plan["candidate_snapshot"]["sha256"] != identity["candidate_cache_snapshot_sha256"]
        ):
            raise ValueError("The warming plan does not match the measured cache experiment")
        measured = benchmark.manifests(args.inputs / f"muzzle-ci-1-{partition}" / "baseline" / "assertions")
        overlap = len(measured.keys() & set(plan["warm_assertion_tasks"])) / len(measured) * 100
        plans[partition] = plan
        lines.append(
            f"| {partition} | {len(plan['warm_seeds'])} | {plan['initial_compressed_bytes'] / 2**30:.2f} GiB "
            f"| {plan['candidate_snapshot']['compressed_bytes'] / 2**30:.2f} GiB | {overlap:.1f}% |"
        )
    lines += [
        "", "Assertion task overlap describes workload reuse, not a measured dependency-cache hit ratio. "
        "Snapshot sizes include the common toolchains and other unchanged caches. "
        "Archive download and setup times are excluded from the Gradle command metric. "
        "This short experiment does not establish long-term cache size or retention.", "",
    ]
    result = ci.aggregate(args.inputs, args.output, args.rounds, args.partitions)
    result["cache_warming"] = plans
    ci.write_json(args.output / "results.json", result)
    summary = args.output / "summary.md"
    summary.write_text(summary.read_text(encoding="utf-8") + "\n".join(lines), encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    prepare = commands.add_parser("warm")
    prepare.add_argument("--repository", type=Path, default=Path(__file__).resolve().parents[1])
    prepare.add_argument("--revision", required=True)
    prepare.add_argument("--partition", type=int, choices=range(1, 5), required=True)
    prepare.add_argument("--warm-seeds", type=int, choices=range(1, 6), default=2)
    prepare.add_argument("--cache-snapshot", type=Path, required=True)
    prepare.add_argument("--snapshot-sha256", required=True)
    prepare.add_argument("--work-dir", type=Path, required=True)
    prepare.add_argument("--output", type=Path, required=True)
    summarize = commands.add_parser("report")
    summarize.add_argument("--inputs", type=Path, required=True)
    summarize.add_argument("--plans", type=Path, required=True)
    summarize.add_argument("--output", type=Path, required=True)
    summarize.add_argument("--rounds", type=int, choices=range(1, 11), required=True)
    summarize.add_argument("--partitions", type=int, choices=range(1, 5), nargs="+", required=True)
    args = parser.parse_args()
    if args.command == "report":
        if args.partitions != sorted(set(args.partitions)):
            parser.error("--partitions must be sorted and unique")
        report(args)
    elif sys.platform != "linux":
        parser.error("Build measurements run on Linux")
    else:
        warm(args)


if __name__ == "__main__":
    main()
