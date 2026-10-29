# Muzzle benchmark

This opt-in benchmark compares real Muzzle partitions in GitHub Actions.
It measures fresh configuration and all selected assertions with a cold Gradle daemon,
warm workspace outputs, and warm dependency caches. It does not predict the duration
of a cold checkout or live Maven downloads.

## Running a comparison

Dispatch `muzzle-performance-comparison.yml` on the revision containing the benchmark:

```sh
gh workflow run muzzle-performance-comparison.yml \
  --ref my-benchmark-branch \
  -f baseline-ref=BASELINE_SHA \
  -f candidate-ref=CANDIDATE_SHA \
  -f partitions='[1,2,3,4]' \
  -f rounds=5
```

The default is partition 1. Each partition runs on its own `ubuntu-24.04` runner;
baseline and candidate always share that runner. Five rounds execute fifteen
measured builds per partition, plus untimed preparation. Each round includes an
identical-revision baseline control. Baseline/candidate order alternates.
Jobs have a two-hour limit.

Use one round for a functional check, not a performance conclusion.
Set `profile=true` to collect separate, untimed JFR profiles.
Normal Muzzle CI remains unchanged.

Both revisions must contain the benchmark controls. To compare older revisions,
pass `support-commit` with the commit that introduces those controls, followed by
any fixes as space-separated commits in application order. The runner applies
each commit's `gradle-plugins/src/main` patch to each older checkout, without
committing or publishing either checkout. Use support-only commits, not commits
containing the optimization under measurement. The local CLI accepts repeated
`--support-commit` options.
The report records the original revisions and support patch hash.

## Fixed inputs

`workload.json` fixes the sample seed and version limit. Sampling is independent
of project visitation and uses frozen repository metadata. The benchmark still
resolves version ranges, filters versions, registers tasks, and executes assertions.
It does not replay a precomputed task graph or replace Muzzle with a synthetic fixture.

Preparation captures Aether's Maven metadata once. Both revisions then use that
same metadata offline. Online, untimed builds fetch the selected library dependencies,
build each revision's shared build logic, and prepare workspace outputs.
After both online builds finish, an offline Gradle Profiler invocation verifies
each prepared revision against the same dependency cache.

Every successful assertion records its path, pass/fail mode, instrumentation
exclusions, ordered classpath identities and SHA-256 hashes, and unresolved dependency outcomes.
The complete records must agree across both revisions and every measured build.
Known historical unresolved transitives remain visible and must match; assertion
failures, missing metadata, or changed dependency graphs invalidate the comparison.
An empty version range is valid when the captured metadata contains releases
outside that range, such as a future stable release with only prereleases published.

The revisions must also agree on the wrapper, Gradle properties, JDK requirement,
and pinned latest-dependency versions. The workflow pins Temurin and Gradle Profiler.

For later comparisons, reuse the `muzzle-benchmark-inputs-N` artifact from an
earlier run by passing `workload-run-id`. It contains frozen metadata and the
expected assertion/dependency records. The runner refuses changed inputs instead
of silently refreshing them. Artifacts expire after 30 days, so download and retain
important workload snapshots. Local runs can pass the retained directory with
`--inputs`.

Omitting that input explicitly creates a new workload snapshot. Do not compare
absolute results across different workload identities.

## Cache conditions

The runner uses the same absolute checkout and Gradle user-home paths for both
revisions. After preparation, it snapshots each workspace and a shared, fully
warmed Gradle user home. Before every measured invocation it restores those
snapshots and removes the root configuration-cache entry.
It stops daemons in its private Gradle user home before copying state.

This keeps build outputs, Kotlin DSL caches, dependency artifacts, and the initial
task state fixed. It also avoids checkout relocation as a source of cache misses.
The benchmark rejects configuration-cache reuse. It deliberately does not measure
the configuration-cache-hit case.

Gradle's dependency resolution and Muzzle's direct Aether resolution both run
offline during measurement. `--offline` alone does not control Aether.
The benchmark disables the remote build cache and retains the local build cache.
There are no timed downloads, cache-clearing builds, or forced task reruns.

## Reading the results

The Actions summary reports the median and full range of total execution times.
Results artifacts contain every raw Gradle Profiler CSV/JSON/HTML report, build
logs, task-cache outcomes, successful assertion records, and environment identity.
Failed runs retain diagnostics and do not publish a successful comparison.
No measured build is retried and no outlier is discarded.

The identical-revision control estimates measurement noise on the same runner.
A small baseline/candidate difference comparable to that noise is inconclusive.
Use more rounds and independent workflow runs before making a small performance
claim. A functional one-round run is explicitly labeled as such.

Gradle Profiler's `task start` measures elapsed time before task execution,
including work beyond Muzzle version enumeration. It is not a pure version-expansion
timer. Cumulative build-operation durations are not elapsed critical-path time.
Artifact auditing is part of each measured build, so compare this benchmark's
results with each other rather than substituting them for ordinary CI step times.

## Local maintenance

The runner uses Python's standard library. Run its unit tests on any supported
Python 3.11+ installation:

```sh
cd benchmark-muzzle
python3 -m unittest -v
```

On Linux, use the same runner directly with a checksum-verified Gradle Profiler
distribution:

```sh
python3 benchmark-muzzle/benchmark.py \
  --baseline BASELINE_SHA --candidate CANDIDATE_SHA \
  --partition 1 --rounds 5 \
  --profiler /absolute/path/to/gradle-profiler \
  --work-dir /absolute/path/to/new-benchmark-directory
```

The work directory must be new. It retains snapshots for diagnostics; remove only
that named directory when finished. Do not run competing builds during measurement.

Format and test changes to the plugin controls with the repository's existing tools:

```sh
./gradlew -p gradle-plugins --init-script "$PWD/benchmark-muzzle/spotless.init.gradle" \
  spotlessApply
./gradlew -p gradle-plugins test --tests '*MuzzleBenchmarkTest'
```
