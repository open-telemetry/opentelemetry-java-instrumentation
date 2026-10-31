# Muzzle benchmark

This opt-in benchmark times direct Gradle Muzzle commands in GitHub Actions.
It measures fresh configuration and all selected assertions with a cold Gradle daemon,
warm workspace outputs, and warm dependency caches. This is a repeatable tuning
scenario, not a prediction of fresh-checkout CI time or live Maven downloads.

There is one benchmark runner, `benchmark.py`. The Gradle plugin's
`MuzzleBenchmarkSupport` class controls and records its workload; it is not a
separate benchmark. This tooling is for performance research and leaves normal
Muzzle CI unchanged.

## Running a comparison

Dispatch `muzzle-performance-comparison.yml` on the revision containing the benchmark:

```sh
gh workflow run muzzle-performance-comparison.yml \
  --ref my-benchmark-branch \
  -f baseline-ref=BASELINE_SHA \
  -f candidate-ref=CANDIDATE_SHA \
  -f partitions='[1]' \
  -f rounds=5
```

The default is partition 1. Each partition runs on its own `ubuntu-24.04` runner;
baseline and candidate always share that runner. Five rounds execute fifteen
measured builds per partition, plus untimed preparation. Each round includes an
identical-revision baseline control. Baseline/candidate order alternates.
Jobs have a two-hour limit, including preparation.
Pass a subset such as `partitions='[3,4]'` to run only those partitions.

Start with identical baseline/candidate revisions, one partition, and one round.
Then verify a baseline/candidate comparison before expanding to all four partitions.
One round is a functional check, not a performance conclusion. Normal Muzzle CI remains unchanged.

Both revisions must contain the same benchmark support code. Start the research
branch from the benchmark commit, then add optimizations on top. Compare that
baseline commit with the optimization commit. The runner rejects missing or
changed support code before starting any builds and does not patch either revision.

## Fixed inputs

`workload.json` fixes the sample seed and version limit. Sampling is independent
of project visitation and uses frozen repository metadata. The benchmark still
resolves version ranges, filters versions, registers tasks, and executes assertions.
It does not replay a precomputed task graph or replace Muzzle with a synthetic fixture.

Preparation captures Aether's Maven metadata once. Both revisions then use that
same metadata offline. Online, untimed builds fetch the selected library dependencies,
build each revision's shared build logic, and prepare workspace outputs.
After both online builds finish, an offline warmup verifies each prepared revision
against the same dependency cache.

Every successful assertion records its path, pass/fail mode, instrumentation
exclusions, ordered classpath identities and SHA-256 hashes, and unresolved dependency outcomes.
The complete records must agree across both revisions and every measured build.
Known historical unresolved transitives remain visible and must match; assertion
failures, missing metadata, or changed dependency graphs invalidate the comparison.
An empty version range is valid when the captured metadata contains releases
outside that range, such as a future stable release with only prereleases published.

The revisions must also agree on instrumentation main sources, the wrapper, Gradle
properties, JDK requirement, and pinned latest-dependency versions. This prevents
changes to the instrumentation fixtures from being mistaken for faster matching.
The workflow pins Temurin.

For later comparisons, pass `workload-run-id` to reuse an earlier run's frozen
metadata and expected assertion/dependency records. The workflow loads `inputs`
from the retained `muzzle-benchmark-results-N` artifact. This also works for
interrupted runs that finished preparation. The runner refuses changed inputs
instead of silently refreshing them.

Successful runs also publish a smaller `muzzle-benchmark-inputs-N` artifact for local
reuse with `--inputs`. Artifacts expire after 30 days, so download and retain important
workload snapshots.

Omitting that input explicitly creates a new workload snapshot. Do not compare
absolute results across different workload identities.

## Cache conditions

Each revision has one fixed checkout for the whole run. Both use a shared, private
Gradle user home, seeded once if requested. Preparation warms each checkout at its
final path. There are no workspace snapshots or per-sample cache copies.

Before each command, the runner removes only that checkout's root configuration-cache
entry and previous assertion records. It uses `--no-daemon` for a cold Gradle process.
The warmed checkouts and caches stay in place between samples. Measured builds
must reuse compilation outputs, execute every expected assertion, and store a fresh
configuration-cache entry. Cache outcomes remain in the reports.

Gradle's dependency resolution and Muzzle's direct Aether resolution both run
offline during measurement. `--offline` alone does not control Aether.
The benchmark disables the remote build cache and retains the local build cache.
There are no timed downloads, cache-clearing builds, or forced task reruns.

## Reading the results

The runner measures each Gradle command with a monotonic wall clock. It does not
launch Gradle Profiler, inspection builds, or extra shutdown commands per sample.
The Actions summary reports the median and full range of command durations.
Results artifacts contain raw CSV/JSON samples, build logs, task-cache outcomes,
successful assertion records, and environment identity.
Failed runs retain diagnostics and do not publish a successful comparison.
No measured build is retried and no outlier is discarded.

The identical-revision control estimates measurement noise on the same runner.
A small baseline/candidate difference comparable to that noise is inconclusive.
Use more rounds and independent workflow runs before making a small performance
claim. A functional one-round run is explicitly labeled as such.

`timing.json` separates initial setup, preparation, measured Gradle commands, and
runner overhead. `phases.json` records every command's duration and process outcome,
including failed commands. Preparation and setup are not performance samples.

There is no configuration-only timing claim. Artifact auditing is part of each
measured build, so compare this benchmark's results with each other rather than
substituting them for ordinary CI step times. Measuring real CI savings requires
a separate experiment that includes its checkout and network conditions.
Both revisions in a comparison must use the same runner and timing method.

## Local maintenance

The runner uses Python's standard library. Run its unit tests on any supported
Python 3.11+ installation:

```sh
cd benchmark-muzzle
python3 -m unittest -v
```

On Linux, use the same runner directly:

```sh
python3 benchmark-muzzle/benchmark.py \
  --baseline BASELINE_SHA --candidate CANDIDATE_SHA \
  --partition 1 --rounds 5 \
  --work-dir /absolute/path/to/new-benchmark-directory
```

The work directory must be new. It retains both checkouts and the Gradle cache for
diagnostics; remove only that named directory when finished.
Do not run competing builds during measurement.

Run the plugin support tests with:

```sh
./gradlew -p gradle-plugins test --tests '*MuzzleBenchmarkSupportTest'
```

## Assertion cache experiments

`muzzle-cache-benchmark.yml` compares the same revision with assertion caching off
and on. Normal Muzzle jobs and version sampling are unchanged.

```sh
gh workflow run muzzle-cache-benchmark.yml \
  --ref my-cache-research-branch \
  -f partitions='[1]' -f rounds=5 -f warm-seeds=10 -f backend=both \
  -f workload-run-id=LOCAL_SMOKE_RUN_ID
```

Start with one partition, one round, one warm seed, and the local backend. Remote
measurements require the repository's existing `DEVELOCITY_ACCESS_KEY` secret.
The local backend disables remote task caching. The remote backend disables
local task caching and permits remote writes under an isolated assertion namespace.
Both retain the dependency cache restored by `setup-gradle`.

Each measured command starts in a newly checked-out repository with a cold daemon
and fresh configuration cache. Preparation freezes Maven metadata, fetches each
sample's dependencies, and prepares compilation caches outside the measured commands.
Fresh-checkout compilation and shading outcomes remain visible in the results.

The scenarios are empty assertion caches, an identical sample after one cache
writer, and new random samples after distinct prior writers. Empty-cache rounds use
separate namespaces. Later random-sample candidates contribute to the next round's
history, as they would in cache-writing CI. Each round includes an identical-revision
uncached control and alternates baseline/candidate order.

Every executed or cached assertion must produce a report identical to an
independently executed assertion for the current seed. A missing report or an
`UP-TO-DATE` assertion invalidates a fresh-checkout measurement.
Reports include assertion mode, exclusions, ordered artifact identities, hashes,
and unresolved dependency outcomes. The opt-in cache additionally fingerprints
instrumentation/tooling/bootstrap/application classpaths, JVM identity, and
OpenTelemetry and Byte Buddy runtime settings. Changing the seed alone does not
invalidate a previously selected version's result.

Results include raw command logs, per-seed assertion reports, cache outcomes,
potential manifest overlap, paired timing differences, and preparation costs.
These experiments isolate task-result caching, not live network or Gradle-home
restore costs. Artifacts have a 30-day retention period.

To verify reuse across jobs, dispatch the remote backend with one round, zero warm
seeds, the original `workload-run-id`, and `probe-namespace` from the earlier remote
run's `identity.json`. The probe prepares only uncached references before
measurement. Every candidate assertion must restore from the earlier remote cache;
one miss invalidates the probe.
