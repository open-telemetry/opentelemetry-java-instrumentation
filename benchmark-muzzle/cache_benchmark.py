#!/usr/bin/env python3
"""Measure opt-in Muzzle result caching with fresh checkouts and frozen metadata."""

import argparse
from collections import Counter
import csv
import json
import os
from pathlib import Path
import sys
import time
import uuid

import benchmark


class CacheBenchmark(benchmark.Benchmark):
    def __init__(self, args):
        if args.backend == "remote" and not os.environ.get("DEVELOCITY_ACCESS_KEY"):
            raise ValueError("Remote-cache measurements require DEVELOCITY_ACCESS_KEY")
        probe_namespace = getattr(args, "probe_namespace", None)
        if probe_namespace is not None and (
            not probe_namespace or args.backend != "remote" or args.inputs is None
            or args.rounds != 1 or args.warm_seeds != 0
        ):
            raise ValueError(
                "A cross-job cache probe requires a namespace, remote backend, frozen inputs, "
                "one round, and zero warm seeds"
            )
        args.baseline = args.candidate = args.revision
        super().__init__(args, dependency_offline=False)
        self.seed = self.workload["seed"]
        self.cache_assertions = False
        self.namespace = probe_namespace or f"muzzle-research-{uuid.uuid4().hex}"
        self.active_namespace = self.namespace
        self.expected_by_seed = {self.seed: self.expected} if self.expected is not None else {}
        self.prior_manifests = {}
        self.writers = 0
        self.fresh_checkouts = 0
        self.cache_samples = []
        if args.backend == "remote":
            self.init_script.write_text(
                "settingsEvaluated { settings ->\n"
                "  if (settings.gradle.parent == null) {\n"
                "    settings.buildCache.local.enabled = false\n"
                "    def remote = settings.buildCache.remote\n"
                "    if (remote == null) { throw new GradleException('Remote cache is missing') }\n"
                "    remote.enabled = true\n"
                "    remote.push = true\n"
                "  }\n"
                "}\n", encoding="utf-8",
            )
        identity_file = self.reports / "identity.json"
        identity = json.loads(identity_file.read_text(encoding="utf-8"))
        identity.update(
            remote_build_cache=args.backend == "remote",
            cache_conditions="fresh checkout, cold daemon, fresh configuration cache, warm dependencies",
            dependency_resolution="online Gradle resolution, frozen offline Aether metadata",
            experiment={
                "backend": args.backend, "namespace": self.namespace,
                "warm_seeds": args.warm_seeds, "rounds": args.rounds,
                "revision": self.revisions["baseline"],
                "cross_job_probe": probe_namespace is not None,
            },
        )
        identity_file.write_text(json.dumps(identity, indent=2) + "\n", encoding="utf-8")

    def properties(self, *, offline):
        return [
            argument for argument in super().properties(offline=offline)
            if not argument.startswith("-PmuzzleBenchmarkSeed=")
        ] + [
            f"-PmuzzleBenchmarkSeed={self.seed}",
            f"-PmuzzleBenchmarkCache={str(self.cache_assertions).lower()}",
            f"-PmuzzleBenchmarkCacheNamespace={self.active_namespace}",
        ]

    def validate_outcomes(self, actual, outcomes, *, preparation):
        assertions = {
            task: outcome for task, outcome in outcomes.items() if ":muzzle-Assert" in task
        }
        benchmark.require_equal(set(actual), set(assertions), "Reported assertions")
        allowed = {"EXECUTED", "FROM-CACHE"} if self.cache_assertions else {"EXECUTED"}
        invalid = {task: outcome for task, outcome in assertions.items() if outcome not in allowed}
        if invalid:
            raise ValueError(f"Invalid fresh-checkout assertion outcomes: {invalid}")

    def build(self, label, name, *, offline, preparation=False):
        self.expected = self.expected_by_seed.get(self.seed)
        result = super().build(label, name, offline=offline, preparation=True)
        if offline:
            self.expected_by_seed[self.seed] = self.expected
        return result

    def fresh_build(self, label, name):
        self.fresh_checkouts += 1
        checkout = self.root / f"checkout-{self.fresh_checkouts}"
        benchmark.run([
            "git", "clone", "--shared", "--quiet", "--no-checkout",
            str(self.source), str(checkout),
        ])
        benchmark.run([
            "git", "-C", str(checkout), "checkout", "--quiet", "--detach",
            self.revisions["baseline"],
        ])
        self.checkouts[label] = checkout
        return self.build(label, name, offline=True)

    def prepare_seed(self, seed, name, *, cache):
        started = time.perf_counter()
        self.seed = seed
        self.cache_assertions = False
        self.build("baseline", f"{name}-dependencies", offline=False, preparation=True)
        self.build("baseline", f"{name}-reference", offline=True, preparation=True)
        if cache:
            self.cache_assertions = True
            self.build("candidate", f"{name}-audit", offline=True, preparation=True)
            self.prior_manifests.update(self.expected)
            self.writers += 1
        self.preparation_ms += (time.perf_counter() - started) * 1000

    def measured_round(self, scenario, number):
        if scenario == "empty":
            self.active_namespace = f"{self.namespace}-empty-{number}"
            prior = {}
            writers = 0
        else:
            self.active_namespace = self.namespace
            prior = dict(self.prior_manifests)
            writers = self.writers
        expected = self.expected_by_seed[self.seed]
        overlap = sum(prior.get(task) == record for task, record in expected.items())
        if scenario == "cross-job":
            overlap = writers = None
        for label in benchmark.order(number - 1):
            self.cache_assertions = label == "candidate"
            source = "candidate" if self.cache_assertions else "baseline"
            metrics, outcomes = self.fresh_build(source, f"round-{scenario}-{number}-{label}")
            assertions = Counter(
                outcome for task, outcome in outcomes.items() if ":muzzle-Assert" in task
            )
            if scenario == "empty" and assertions.get("FROM-CACHE", 0):
                raise ValueError("An isolated empty assertion cache returned cached results")
            self.cache_samples.append({
                "scenario": scenario, "round": number, "label": label,
                "seed": self.seed, "prior_writers": writers,
                "prior_manifest_overlap": overlap,
                "namespace": self.active_namespace,
                "metrics": metrics, "assertions": dict(assertions), "task_outcomes": outcomes,
            })
            if self.cache_assertions and scenario not in ("empty", "cross-job"):
                self.prior_manifests.update(expected)
                self.writers += 1
            self.write_results()
            if (
                scenario == "same" and self.cache_assertions
                and assertions.get("FROM-CACHE", 0) != len(expected)
            ):
                raise ValueError(
                    f"The same-sample cache did not restore every assertion: {dict(assertions)}"
                )

    def measure(self):
        started = time.perf_counter()
        try:
            super().prepare()
        finally:
            self.preparation_ms += (time.perf_counter() - started) * 1000
        self.expected_by_seed[self.seed] = self.expected
        if getattr(self.args, "probe_namespace", None) is not None:
            self.measured_round("cross-job", 1)
            assertions = next(
                sample["assertions"] for sample in self.cache_samples
                if sample["label"] == "candidate"
            )
            if assertions.get("FROM-CACHE", 0) != len(self.expected):
                raise ValueError(f"The cross-job probe did not restore every assertion: {assertions}")
            self.write_results(complete=True)
            return
        for number in range(1, self.args.rounds + 1):
            self.measured_round("empty", number)
        self.active_namespace = self.namespace
        self.prepare_seed(self.workload["seed"], "same-sample-writer", cache=True)
        for number in range(1, self.args.rounds + 1):
            self.measured_round("same", number)
        for number in range(1, self.args.warm_seeds + 1):
            self.prepare_seed(self.workload["seed"] + number, f"prior-seed-{number}", cache=True)
            self.write_results()
        for number in range(1, self.args.rounds + 1):
            self.prepare_seed(
                self.workload["seed"] + 100000 + number, f"new-seed-{number}", cache=False,
            )
            self.measured_round("random", number)
        self.write_results(complete=True)

    def write_results(self, *, complete=False):
        (self.reports / "cache-samples.json").write_text(
            json.dumps({"complete": complete, "samples": self.cache_samples}, indent=2) + "\n",
            encoding="utf-8",
        )
        with (self.reports / "cache-samples.csv").open("w", encoding="utf-8", newline="") as stream:
            writer = csv.writer(stream)
            writer.writerow([
                "scenario", "round", "label", "seed", "prior_writers",
                "prior_manifest_overlap", "total_ms", "executed_assertions", "cached_assertions",
            ])
            for sample in self.cache_samples:
                writer.writerow([
                    sample["scenario"], sample["round"], sample["label"],
                    sample["seed"], sample["prior_writers"], sample["prior_manifest_overlap"],
                    sample["metrics"]["total execution time"],
                    sample["assertions"].get("EXECUTED", 0),
                    sample["assertions"].get("FROM-CACHE", 0),
                ])
        measured_ms = sum(s["metrics"]["total execution time"] for s in self.cache_samples)
        total_ms = (time.perf_counter() - self.started) * 1000
        (self.reports / "timing.json").write_text(json.dumps({
            "setup_ms": self.setup_ms, "preparation_ms": self.preparation_ms,
            "measured_gradle_ms": measured_ms,
            "overhead_ms": total_ms - self.setup_ms - self.preparation_ms - measured_ms,
            "total_ms": total_ms,
        }, indent=2) + "\n", encoding="utf-8")
        lines = [
            f"# Muzzle cache partition {self.args.partition}, {self.args.backend}",
            "",
            "Every measured command uses a fresh checkout and cold daemon. Aether version metadata "
            "is frozen and offline. Gradle dependency resolution remains online so remote build "
            "caching is available; dependency requests are included in command timings. "
            "Dependencies are prepared before measurement. Baseline and control execute "
            "all assertions; candidate enables result caching. Cached reports must exactly match "
            "the current seed's independently executed assertions.",
            "",
            "| Scenario | Label | Samples | Median | Min | Max | Assertion cache hits |",
            "| --- | --- | ---: | ---: | ---: | ---: | ---: |",
        ]
        stats = {}
        for scenario in ("empty", "same", "random", "cross-job"):
            stats[scenario] = {}
            for label in ("baseline", "control", "candidate"):
                samples = [
                    s for s in self.cache_samples if s["scenario"] == scenario and s["label"] == label
                ]
                if not samples:
                    continue
                values = benchmark.distribution([
                    s["metrics"]["total execution time"] for s in samples
                ])
                cached = sum(s["assertions"].get("FROM-CACHE", 0) for s in samples)
                assertions = sum(sum(s["assertions"].values()) for s in samples)
                values["assertion_cache_hit_ratio"] = cached / assertions
                stats[scenario][label] = values
                lines.append(
                    f"| {scenario} | {label} | {len(samples)} | {values['median_ms'] / 1000:.2f}s "
                    f"| {values['min_ms'] / 1000:.2f}s | {values['max_ms'] / 1000:.2f}s "
                    f"| {cached / assertions:.1%} |"
                )
            pairs = {}
            for sample in self.cache_samples:
                if sample["scenario"] == scenario:
                    pairs.setdefault(sample["round"], {})[sample["label"]] = sample["metrics"]["total execution time"]
            stats[scenario]["paired_changes_ms"] = {
                label: benchmark.distribution([
                    pair[label] - pair["baseline"] for pair in pairs.values()
                    if label in pair and "baseline" in pair
                ])
                for label in ("control", "candidate")
                if any(label in pair and "baseline" in pair for pair in pairs.values())
            }
        (self.reports / "statistics.json").write_text(
            json.dumps(stats, indent=2) + "\n", encoding="utf-8",
        )
        history = (
            "The cross-job probe reuses an earlier remote namespace. No assertion writer runs "
            "in this job before measurement. Every candidate assertion must restore from the "
            "remote cache and match the uncached reference."
            if getattr(self.args, "probe_namespace", None) is not None else
            "Empty-cache candidates have independent namespaces. Same-sample measurements follow "
            "one writer. Random-sample measurements follow that writer and "
            f"{self.args.warm_seeds} distinct prior seeds; each measured candidate also contributes "
            "to subsequent cache history. Exact writer counts and manifest overlap are in the CSV."
        )
        lines += [
            "",
            history,
            "",
            "Fresh-checkout compilation and shading outcomes are retained, not assumed away. "
            "Preparation and checkout creation are excluded from command timings. This isolates "
            "assertion caching on a prepared workload; Gradle network requests are included, but "
            "Gradle-home restoration and live version-metadata resolution remain separate.",
            "",
            f"Complete: {complete}. No samples are retried or discarded.",
        ]
        if self.args.rounds < 5:
            lines += ["", "**Functional run only. Fewer than five rounds cannot support a performance claim.**"]
        (self.reports / "summary.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--revision", required=True)
    parser.add_argument("--work-dir", type=Path, required=True)
    parser.add_argument("--partition", type=int, choices=range(1, 5), required=True)
    parser.add_argument("--rounds", type=int, choices=range(1, 11), default=5)
    parser.add_argument("--warm-seeds", type=int, choices=range(0, 101), default=10)
    parser.add_argument("--backend", choices=("local", "remote"), default="local")
    parser.add_argument("--probe-namespace")
    parser.add_argument("--inputs", type=Path)
    parser.add_argument("--seed-gradle-home", type=Path)
    args = parser.parse_args()
    if sys.platform != "linux":
        parser.error("Build measurements run on Linux")
    runner = CacheBenchmark(args)
    try:
        runner.measure()
    except (OSError, ValueError, benchmark.subprocess.CalledProcessError) as e:
        (runner.reports / "failure.txt").write_text(str(e) + "\n", encoding="utf-8")
        runner.write_results()
        raise


if __name__ == "__main__":
    main()
