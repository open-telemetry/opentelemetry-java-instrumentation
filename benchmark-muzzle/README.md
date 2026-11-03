# Cold-checkout Muzzle benchmark

`trask-muzzle-benchmark` is a reusable baseline for Muzzle optimization branches, not an optimization
itself. It starts from main commit `e4f28d52cd0d26b3619de8c61b2ebc3ce9e831f6` and extracts the
cold-checkout tooling from research commit `5a111f02307ca9aa2a091a230520b44ac76ae49a`.

Normal Muzzle runs keep main's eager version discovery, random sampling, compatibility assertions,
four-slice task layout, cache policy, disk cleanup, and network settings. Only benchmark runs opt into
seeded sampling and workload recording.

## Start a comparison

Create the candidate from this baseline. Do not compare against plain main, which lacks the recording
hooks. Use commit SHAs so a branch moving during the run cannot change the compared code.

```bash
git fetch origin main trask-muzzle-benchmark
git switch -c trask-muzzle-candidate origin/trask-muzzle-benchmark
baseline=$(git rev-parse HEAD)
# Implement the optimization, commit it, and push this candidate branch.
git push -u origin trask-muzzle-candidate
candidate=$(git rev-parse HEAD)
gh workflow run muzzle-performance-comparison.yml \
  --repo open-telemetry/opentelemetry-java-instrumentation \
  --ref trask-muzzle-benchmark \
  -f baseline-ref="$baseline" -f candidate-ref="$candidate" \
  -f rounds=5 -f partitions='[1,2,3,4]'
```

The workflow revision supplies the runner and init scripts. Both compared revisions must contain
identical `MuzzleBenchmarkSupport.java`, pinned versions, `.java-version`, `gradle.properties`, and
Gradle wrapper properties. Instrumentation `src/main` trees must also match. The runner rejects drift
in these inputs before building. Candidates can change Muzzle implementation and build logic, but
must not change the compatibility workload.

For an identical-ref smoke run, use the baseline SHA for both refs, `-f rounds=1`, and
`-f partitions='[1]'`. This still executes baseline, control, and candidate commands. It verifies
functionality, not performance.

Find the dispatched run, wait for completion, and inspect the verified report:

```bash
gh run list --repo open-telemetry/opentelemetry-java-instrumentation \
  --workflow muzzle-performance-comparison.yml --branch trask-muzzle-benchmark --limit 5
gh run watch RUN_ID --repo open-telemetry/opentelemetry-java-instrumentation --exit-status
gh run download RUN_ID --repo open-telemetry/opentelemetry-java-instrumentation \
  --name muzzle-ci-summary --dir benchmark-muzzle/build/summary
```

Five rounds across all four slices create 20 measurement jobs and 60 Gradle commands, plus tooling
verification, snapshot preparation, and reporting. Each measurement job runs its three commands
sequentially. Use the smoke run first.

## Measurement conditions

The preparation job restores the normal Gradle Actions cache read-only and freezes it before any
build in that job. Every sample gets a fresh checkout and a separate Gradle home extracted from
that one immutable snapshot. No reference build warms a sample. Checkout-local Aether metadata and
configuration caches start empty. Version discovery and dependency resolution stay online.

Each job compares baseline, an identical-baseline control, and candidate. Odd rounds run in that
order; even rounds reverse it. The control measures runner, ordering, network, and live remote-cache
variation without a source change.

`workload.json` fixes seed `1729` and the production sampling limit of ten versions. Seeded selection
sorts eligible versions before shuffling and includes eligible range endpoints. Releases above the
pin and ineligible prereleases cannot perturb the sample. Unseeded runs retain main's sampling.
Missing discovery metadata fails the benchmark rather than silently shrinking its work.

Successful assertions record their full task identity, pass/fail expectation, exclusions, ordered
dependency coordinates, JAR names and SHA-256 hashes, and unresolved dependency failures. Every
record must match across baseline, control, candidate, and all rounds for its slice. Assertions must
execute, never restore from a task cache. The report rechecks the records, console outcomes,
captured metadata, and Gradle profiles. Failed or incomplete jobs and changed workloads invalidate
the comparison; the runner neither retries samples nor drops outliers.

Gradle's local task cache and remote reads remain enabled. Init hooks disable remote pushes in every
build, including included builds. The runner refuses `DEVELOCITY_ACCESS_KEY`. All
`setup-gradle` steps are read-only, and the workflow creates no persistent cache entries or
accumulated dependency cache. Snapshot artifacts are temporary benchmark inputs, not a proposed
cache policy. They expire after seven days and must fit within 10 GB.

To reuse exactly the same snapshot in a later comparison, pass `-f source-run=RUN_ID` while its
`muzzle-ci-cache-snapshot` artifact still exists. This preserves the local seed, not the contents
of the live remote cache.

## Read the results

`muzzle-ci-summary` contains `results.json` and `summary.md`. The primary metric takes the maximum
Gradle command duration across slices separately in each round, then reports paired candidate and
control changes and their spread. A negative change means a shorter command.

Command duration is not observed CI fan-out wall time, runner queue time, or total PR time.
Checkout and cache extraction are separate costs. Runner allocation, Java setup, artifact download,
tooling verification, and report generation are outside the command metric. A benchmark job's
elapsed time also includes all three sequential commands.

Compare the candidate effect with the identical-baseline control, including the paired ranges.
Inspect task-cache outcomes before attributing a difference to the optimization. Unrelated main
builds can populate the remote cache during the run, so read-only access does not make its contents
immutable. The report retains differing cache-hit counts instead of claiming equivalent cache reads.

Each `muzzle-ci-ROUND-SLICE` artifact retains commands, logs, assertion records, metadata hashes,
Gradle HTML profiles, task outcomes, disk usage, and setup timings. Time to the main task graph
includes JVM startup and included-build work as well as project configuration. Summed assertion
task durations are not parallel wall time.

Add `-f operations-profile=true` for diagnostic comparisons. Every sample then uses the same
`operations.init.gradle` listener and records `operations.jsonl` plus its summary in `samples.json`.
Its exclusive wall-time categories avoid double-counting overlapping operations, but are not
critical-path or CPU attribution. Gradle transfer events do not cover Aether's independent HTTP
client. Profiling and workload hashing add overhead, so do not mix profiled and unprofiled runs.
Transfer URLs omit user info, queries, and fragments. No JFR, environment, or system-property
recordings are collected. Review logs and artifacts before sharing them.

## Maintain and verify the tooling

The cold-checkout runner is `ci_benchmark.py`; `benchmark_support.py` holds shared identity and
process helpers. `ci.init.gradle` enforces read-only remote access and records task-graph timing.
`operation_profile.py` interprets the optional operation recording.

The Muzzle plugin recognizes `-PmuzzleBenchmarkSeed=1729` and
`-PmuzzleBenchmarkReportDirectory=PATH`. Reporting requires a seed. These are development-only
controls; they do not cache assertion results or select discovery projects.

Use Python 3.12 or newer for unit tests and reporting. Measurements run on Linux with the JDK
selected by the compared `.java-version`. Local tests also support Windows.

```bash
python3 -m unittest discover -s benchmark-muzzle -v
./gradlew -p gradle-plugins test \
  --tests '*MuzzleBenchmarkSupportTest' --tests '*MuzzleBenchmarkFunctionalTest'
mise exec -- rumdl check --fix --config .github/config/.rumdl.toml benchmark-muzzle/README.md
```

The focused Gradle tests cover seeded cohorts, positive and negative recording, unresolved
dependencies, assertion failures, configuration-cache reuse, and eager discovery in normal and
seeded runs. Keep these contracts when editing the hooks. Keep workflow action pins and runtime
selection aligned with main.

To regenerate a report without rerunning builds, download the measurement artifacts into a new
directory and run the same workload verification:

```bash
gh run download RUN_ID --repo open-telemetry/opentelemetry-java-instrumentation \
  --pattern 'muzzle-ci-[0-9]*-*' --dir benchmark-muzzle/build/replay
python3 benchmark-muzzle/ci_benchmark.py report \
  --inputs benchmark-muzzle/build/replay --output benchmark-muzzle/build/replayed-summary \
  --rounds 5 --partitions 1 2 3 4
```

Update the clean baseline from main independently of candidate branches. Record the new main base,
run the focused checks and identical-ref smoke, then start new candidates from that baseline.
Do not merge candidate optimizations back into it.
