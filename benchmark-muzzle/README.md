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
Set `source-run=EARLIER_CI_BENCHMARK_RUN` to reuse that run's immutable
`muzzle-ci-cache-snapshot` instead, without creating and re-uploading a new
snapshot.
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
Seeded samples choose their bounds and shuffle from the pinned eligible
version set. Releases above the pinned bound or rejected prereleases do not
change that sample. Production's unseeded sampling is unchanged.
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

Build-logic changes can invalidate compilation and other task-cache entries.
Check the recorded task outcomes before attributing an overall timing change
to version discovery. Fresh-checkout results do not by themselves predict cache
reuse after the candidate has merged.

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

## Profiling individual commands

Collect a cold-checkout operation profile without running paired comparisons:

```sh
gh workflow run muzzle-performance-comparison.yml \
  --ref my-benchmark-branch \
  -f profile-only=true -f source-run=EARLIER_CI_BENCHMARK_RUN \
  -f baseline-ref=REVISION_SHA -f partitions='[1,2,3,4]'
```

This runs one command per slice using the source run's original snapshot.
It writes no shared caches and does not create warmed snapshots.
Each `muzzle-profile-SLICE` artifact includes `operations.jsonl`, the ordinary
Gradle profile, exact assertion records, metadata and command diagnostics.

The operation listener records overlapping Gradle resource reads, dependency
resolution, configuration and task intervals. The summary assigns each
millisecond to the highest-priority active category, rather than adding
concurrent operation durations. This is a wall-time classification, not a
CPU or critical-path profile. Resource reads include HTTP-client setup,
queuing and response waits. Aether's independent metadata requests are not
Gradle resource-read operations.

`--operations-profile` also enables these diagnostics for a direct
`ci_benchmark.py run` comparison. `--single-profile` records one command and
cannot produce a paired performance comparison.

`candidate-mode=configuration-cache-off` tests whether storing an unused
configuration cache costs time on fresh CI checkouts. It requires identical
baseline and candidate source revisions and cache snapshots. Only candidate
adds `--no-configuration-cache`; task-cache reads remain enabled. Use it with
`profile-only=true` for a single candidate diagnostic, or without
`profile-only` for repeated paired comparisons. Both forms verify the actual
configuration-cache mode and complete assertion/dependency records.

`candidate-mode=http-connection-ttl` instead tests a 10-second maximum HTTP
connection lifetime. Gradle 9.8.2 maps
`org.gradle.internal.http.idleConnectionTimeout` to Apache HTTP client's
connection time-to-live, with a default of three minutes. The experiment
leaves connection and read timeouts and retry counts unchanged. It probes
whether replacing older pooled connections reduces the long successful
resource reads seen in profiles; stale connections are a hypothesis, not
an established diagnosis.

`candidate-mode=network-profile` with `profile-only=true` adds a Flight
Recorder diagnostic to the single-command profile. It captures socket
read/write waits, execution samples and long thread/monitor waits inside
the Gradle daemon. A build-operation listener flushes `network.jfr` before the command
finishes. This mode is diagnostic, not timing-comparison evidence; it does
not change network deadlines, retries or dependency selection.

`candidate-mode=slice-discovery` compares eager discovery against the
slice-aware implementation using identical source revisions and compiled
build logic. Baseline and control set the benchmark-only
`muzzleBenchmarkDiscoverAll=true`; candidate sets it to `false`.
This avoids comparing a baseline with cached build logic against a candidate
that must compile changed build logic. Compatibility assertions, task caches,
online dependencies and cold live metadata remain unchanged.

## Four versus eight slices

The slice-count comparison uses the same source revision and compiled build
logic for both layouts. The source must support `-PmuzzleSlices=4` and
`-PmuzzleSlices=8`. Local builds default to four; repository CI explicitly
selects eight. Both layouts use the slice-aware discovery implementation.
This isolates the slice-count change on the research revision, not a literal
before/after comparison with an older eager-discovery revision.

```sh
gh workflow run muzzle-performance-comparison.yml \
  --ref my-benchmark-branch -f candidate-mode=eight-slices \
  -f baseline-ref=REVISION_SHA -f source-run=EARLIER_CI_BENCHMARK_RUN \
  -f rounds=5 -f partitions='[1,2,3,4]'
```

Each round/group measures a four-slice baseline, an identical control, and
the corresponding two eight-slice commands. For example, four-slice
`muzzle2` must equal the union of eight-slice `muzzle2` and `muzzle6`.
The four commands use independent checkouts and Gradle homes, and reverse
order in even rounds. Five rounds use twenty jobs and eighty workload
commands, not eighty jobs.

Reports reject changed dependency records, overlapping or missing checks,
changed metadata coverage, and different task sets. Aggregate
slice tasks themselves are excluded from task-set equality because
the layouts intentionally contain different aggregate task names.
Live remote-cache reads can miss even for the identical control. The report
records every varying task outcome and its observed profile duration.
An adverse sensitivity adds those task durations to candidate commands and
removes them from baseline commands. This is diagnostic, not a formal causal
bound; unprofiled varying tasks make the sensitivity incomplete. Compare
the raw effect, identical control and cache sensitivity before claiming a win.
Each round compares the maximum of four baseline commands with the maximum
of eight candidate commands. This is not an observed Actions fan-out
completion time or total PR time.

Eight slices duplicate more startup, project configuration and shared
prerequisites. They consume more runner time but do not require a larger
persistent dependency cache. `rounds=1` and `partitions='[2]'` provide a
functional calibration of one baseline group and its two halves, not a
global performance claim.
Add `report-run=SLICE_COUNT_RUN` to regenerate its report without repeating
the four workload commands.

## Disk preparation

`candidate-mode=disk-preparation` measures unconditional SDK/image cleanup
against the Muzzle free-space guard. It runs five paired preparation rounds
on fresh `ubuntu-latest` runners, matching production Muzzle's runner image:

```sh
gh workflow run muzzle-performance-comparison.yml \
  --ref my-benchmark-branch -f baseline-ref=REVISION_SHA \
  -f candidate-mode=disk-preparation -f rounds=5
```

Both scripts run on the same runner, alternating order across rounds.
Preparation pairs use scripts from the selected workflow revision.
Only preparation is timed; no Gradle command runs in these pairs.
The guard leaves installed tools alone when at least 50 GiB is free and
otherwise calls the existing cleanup script. This is ephemeral runner
storage, not an allocation from the 10 GB Actions cache budget.

Use the same mode with `profile-only=true`, `source-run` and all four
`partitions` to validate actual Muzzle work without unconditional cleanup.
Those commands retain the exact assertion/dependency records and sample
free disk space once per second during Gradle execution.
Their runner image is `ubuntu-latest`; do not treat comparisons with the
ordinary Ubuntu 24.04 profiles as paired Gradle timing evidence.
`candidate-mode=disk-preparation-control` with `profile-only=true` runs
unconditional cleanup on that same production image for a matched
functional and task-cache control.

## Dependency-cache accumulation

`muzzle-dependency-cache.yml` compares identical source code with the original
Gradle cache versus dependencies accumulated from differently seeded samples.
Start with slice 2, the usual slowest slice, and two prior samples:

```sh
gh workflow run muzzle-performance-comparison.yml \
  --ref my-benchmark-branch \
  -f dependency-cache=true \
  -f source-run=EARLIER_CI_BENCHMARK_RUN \
  -f baseline-ref=REVISION_SHA \
  -f partitions='[2]' -f warm-seeds=2 -f rounds=5
```

The source run must still retain `muzzle-ci-cache-snapshot`. Each prior sample
gets a fresh checkout and cold Aether metadata, but retains its Gradle home
for the next prior sample. Warmups use seeds 1730 onwards; measurements use
seed 1729. Assertions execute in warmups as well as measurements.

The final candidate snapshot replaces only `caches/modules-2` in the original
snapshot. Compilation outputs, task caches, script caches, wrapper and
toolchains come from the original snapshot on both sides. Baseline and control
use the original snapshot. Candidate uses the dependency-warmed snapshot.
Every measured command still gets its own fresh checkout and independent home,
with live version discovery and remote task-cache writes disabled.

This models retaining dependencies between main runs with
`cache-cleanup: never`; it does not change production workflows or write to the
shared Actions cache. The pinned setup-gradle action's
[cleanup integration test](https://github.com/gradle/actions/blob/3f5f9adaf7d9fecd50b5935e54106014257a94e6/.github/workflows/integ-test-cache-cleanup.yml)
confirms that default cleanup removes versions unused by the next build.
Enabling writes alone therefore does not guarantee accumulation.

Warming rejects an initial, intermediate or final snapshot larger than
10,000,000,000 bytes. It stops before another warmup or snapshot upload.
`--snapshot-budget-bytes` can lower that safety cap, but cannot raise it.
Reports preserve measurements from earlier oversized experiments and mark
them **rejected for rollout**.

The cap is not a cache allocation. GitHub's 10 GB repository-wide budget is
shared with every other job and retained cache generation. Snapshots below
the cap remain **storage feasibility unverified** until their actual
production cache entries fit a measured allocation. Gzip benchmark archives
do not establish the size of setup-gradle's cache entries.

The report includes the prior seeds, snapshot sizes and measured assertion
task overlap with prior samples. That overlap is not a dependency-cache hit
ratio. A short run does not establish long-term storage growth or retention.
Single-slice results do not establish the maximum across all four slices;
use `partitions='[1,2,3,4]'` for that comparison.

To regenerate a dependency-cache report without repeating builds, add
`-f report-run=DEPENDENCY_CACHE_RUN` to the dispatch command. Keep the same
`rounds` and `partitions`; the report verifies the original measurement and
warming identities. Neither cache snapshot is downloaded in this mode.

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
