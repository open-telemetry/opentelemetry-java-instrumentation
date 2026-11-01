# Muzzle benchmark

The primary benchmark compares fresh-checkout Muzzle commands with live Maven
version discovery. It measures the maximum command duration across all four
slices, with repeated paired samples and an identical-revision control.
It does not change production Muzzle CI, random sampling, or assertion caching.

## Running a comparison

Dispatch the workflow on the branch containing the benchmark:

```sh
gh workflow run muzzle-performance-comparison.yml \
  --ref my-benchmark-branch \
  -f baseline-ref=BASELINE_SHA \
  -f candidate-ref=CANDIDATE_SHA \
  -f rounds=5
```

The default is all four slices. `partitions='[1]'` and `rounds=1` provide a
functional check, not performance evidence.

The workflow captures one Gradle-cache snapshot before executing any builds.
Every round/slice pair runs in its own job, so five rounds create twenty
independent jobs. Each job executes baseline, an identical-baseline control,
and candidate on the same runner. Even rounds reverse baseline/candidate order.
The complete comparison uses sixty workload builds, with no preparation builds.
Jobs can run concurrently subject to the repository's runner availability.

Both revisions must already contain identical `MuzzleBenchmarkSupport` code.
The runner does not patch either revision. It rejects differences in
instrumentation main sources, required JDK, Gradle properties, wrapper, and
pinned dependency versions. The workflow pins the runner OS and JDK.

## Starting conditions

Each individual command has:

- A fresh checkout with no workspace outputs or project configuration cache.
- A new `GRADLE_USER_HOME`, independently extracted from the same immutable
  snapshot. All jobs verify that snapshot's SHA-256.
- Empty Aether metadata directories and live Maven version discovery.
- Online Gradle dependency resolution and normal local task caching.
- Production remote-cache reads, with writes explicitly disabled in every
  build, including included builds.
- A cold Gradle daemon and the repository's normal Gradle properties.

The first command cannot populate the local caches or workspace of the next.
The snapshot contains the restored `caches`, `wrapper`, and `jdks` directories
and any `gradle.properties`, but not daemons, locks, or Actions init scripts.
Do not pass remote-cache writer credentials to this benchmark.

Main builds may populate the remote cache while the comparison is running.
OS filesystem caches, Maven/CDN caching, hardware differences between jobs, and
network conditions also remain sources of variation. Reversed execution order
and the identical-revision control help expose these effects; they do not
guarantee equal cache hits or network latency.

## Workload verification

`workload.json` fixes the sample seed and version limit. The benchmark performs
real version-range resolution, sampling, task registration, and assertions.
It does not freeze metadata or replay a precomputed task graph.

Every assertion must execute successfully without assertion-result caching.
Each records its task identity, pass/fail mode, exclusions, ordered dependency
identities and hashes, and unresolved dependency outcomes.
The baseline is the workload reference. Candidate and control must produce
identical records, including when candidate runs first.

The report rechecks the full records across all revisions and rounds. New
library releases, changed transitive resolutions, or missing artifacts that
change the workload invalidate the comparison instead of appearing as a
speedup. Historical unresolved dependencies remain visible and must match.
There are no automatic retries or discarded outliers.

## Timing and interpretation

The primary timer surrounds only the Gradle process. It includes startup,
configuration, included-build work, live dependency and metadata requests,
compilation/cache restoration, assertions, recording, and profile generation.
Unlike the frozen-workspace experiment, compilation outputs need not already
exist. Task outcomes show what executed or came from cache.

For each round, the report takes the maximum command duration across slices
separately for baseline, control, and candidate, then calculates paired changes.
It summarizes those round-level changes, not a ratio of unrelated medians or
the maximum of per-slice medians.

This is the maximum measured Muzzle **command** duration, not an observed Actions
fan-out completion time or total PR completion time. The paired jobs do not
start each revision's four slices simultaneously.
Runner allocation, Actions setup, snapshot downloading, checkout, and local
cache extraction are outside the primary timer. Checkout and extraction
durations are recorded and reported separately. Job setup timings remain
available in Actions.

Diagnostics from the same commands include:

- Time to the main task graph, which includes startup, included-build tasks,
  configuration, and discovery, not just version discovery.
- Gradle's built-in profile, including project configuration and task execution.
- Summed assertion-task duration, which is not parallel execution wall time.
- Projects performing discovery, captured metadata documents and hashes,
  task-cache outcomes, and complete assertion records.

Metadata-document counts are not HTTP request counts; retries and checksum
requests can produce more requests than captured XML documents.
The same reporting and profiling overhead applies to both revisions.
Compare candidate changes with the identical-revision control before making
a performance claim. A small effect comparable to control variation is
inconclusive. Improvements to Muzzle affect PR completion only when Muzzle is
on the critical path.

## Results and maintenance

`muzzle-ci-summary` contains the verified cross-slice summary and raw estimators.
Each `muzzle-ci-ROUND-SLICE` artifact contains identities, commands, durations,
logs, profiles, captured metadata, task outcomes, and assertion records.
Raw evidence is retained for thirty days; the shared cache snapshot for seven.
Download important evidence before it expires.

Failed commands retain their log and partial sample record. Workload
mismatches prevent a successful summary. Successful samples remove only their
own checkout and mutable Gradle home after collecting evidence.

The runner uses Python's standard library. Run all runner tests with:

```sh
python3 -m unittest discover -s benchmark-muzzle -v
```

`ci_benchmark.py` has `snapshot`, `run`, and `report` subcommands; use `--help`
on each for their inputs. Measurements run on Linux, while unit tests also
support Windows.

## Frozen-workspace comparison

`benchmark.py` retains the separate tuning experiment for a cold daemon and
configuration cache, warmed workspace outputs, and frozen Aether metadata.
Its repeated samples intentionally exclude live metadata lookup. Do not use
that condition as the main evidence for discovery optimizations.

This runner is available directly on Linux:

```sh
python3 benchmark-muzzle/benchmark.py \
  --baseline BASELINE_SHA --candidate CANDIDATE_SHA \
  --partition 1 --rounds 5 \
  --work-dir /absolute/path/to/new-benchmark-directory
```

Both revisions share prepared checkouts and a Gradle home in that experiment.
`--online-dependencies` combines preparation into one build per revision and
keeps Gradle resolution online. `--live-metadata-pair` adds a single diagnostic
pair with empty Aether repositories. Neither option turns the repeated rounds
into fresh-checkout measurements.

## Assertion cache experiments

`muzzle-cache-benchmark.yml` compares the same revision with assertion caching
off and on. Normal Muzzle jobs and version sampling are unchanged.

```sh
gh workflow run muzzle-cache-benchmark.yml \
  --ref my-cache-research-branch \
  -f partitions='[1]' -f rounds=5 -f warm-seeds=10 -f backend=both \
  -f workload-run-id=LOCAL_SMOKE_RUN_ID
```

Start with one partition, one round, one warm seed, and the local backend.
Remote measurements require the repository's existing `DEVELOCITY_ACCESS_KEY`
secret. The local backend disables remote task caching. The remote backend
disables local task caching in the main build and permits remote writes under
an isolated assertion namespace. Its included build-logic projects retain
their existing cache settings. These writer experiments are separate from
the read-only CI comparison.

Each measured command starts in a fresh checkout with a cold daemon and fresh
configuration cache. Preparation freezes metadata, fetches dependencies, and
prepares compilation caches outside the measured commands. Both backends keep
Gradle resolution online because Develocity disables its remote cache under
`--offline`. Aether metadata remains frozen and offline.
Each seed's uncached build both fetches dependencies and records an independent
reference; a separate cache writer is checked against that reference.

Scenarios cover empty assertion caches, an identical sample after one writer,
and new random samples after distinct writers. Each round includes an uncached
identical-revision control and alternates baseline/candidate order. Later
random candidates contribute cache history to subsequent rounds.

Every executed or cached assertion must match the independent reference.
An `UP-TO-DATE` assertion invalidates a fresh-checkout sample. Identical-sample
candidates must restore every assertion; misses invalidate the probe.
The opt-in cache fingerprints instrumentation, tooling, bootstrap and
application classpaths, JVM identity, and relevant runtime settings.
Changing only the sampling seed does not invalidate an identical assertion.

Pass `workload-run-id` to reuse an earlier cache run's frozen inputs. Input
identities must match; online and offline dependency resolution can produce
different failure messages for missing historical dependencies.

For a cross-job reuse probe, dispatch the remote backend with one round, zero
warm seeds, the original workload run, and its `probe-namespace`. The probe
prepares only uncached references. Every candidate assertion must restore
from the earlier remote cache. Cache experiment artifacts expire after thirty
days.
