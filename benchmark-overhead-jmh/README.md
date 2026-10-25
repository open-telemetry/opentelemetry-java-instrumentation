# Servlet overhead benchmarks

This JMH workload makes HTTP requests to a small Spring servlet application.
It measures steady-state request time and JVM-wide allocations, including the
HTTP client. Exporters are disabled, so it does not measure telemetry delivery
or span-data conversion for export.

## Local usage

The existing Gradle task builds and benchmarks the current agent:

```sh
./gradlew :benchmark-overhead-jmh:jmh
```

Use `-Pjmh.includes=ServletBenchmark.execute` to narrow the benchmark.
The project also has agent-disabled, SDK-disabled, 1%-sampling, and unsampled
variants. The default benchmark samples all traces.

Build a reusable benchmark artifact without building an agent:

```sh
./gradlew :benchmark-overhead-jmh:jmhJar
```

The result is `benchmark-overhead-jmh/build/libs/servlet-benchmarks.jar`.
Use the same artifact for both sides of an agent comparison.

## Bare-metal comparisons

Dispatch **Overhead benchmark (daily)** with one of these `benchmark` inputs:

| Input | Purpose |
| --- | --- |
| `servlet-probe` | Check runner scheduling and CPU topology without building |
| `servlet-smoke` | Check artifact loading, servlet behavior, and result collection |
| `servlet` | Measure three fresh forks for each agent and sampling variant |
| `servlet-profile` | Collect separate JFR profiles for the fully sampled agents and no-agent control |

The existing `petclinic` default and nightly schedule are unchanged.

`baseline-ref` and `candidate-ref` select agent revisions in this repository.
Empty refs use the workflow commit, producing an A/A comparison. The harness
always comes from the workflow commit. Refs are resolved to commit IDs during
the build and recorded alongside artifact hashes.

Builds run on ordinary hosted runners. Measurements run serially on
`oracle-bare-metal-64cpu-1024gb-x86-64-ubuntu-24` in a digest-pinned JDK container.
The runner selects four distinct allowed physical cores on one NUMA node,
fixes the JVM processor count to four, and uses a 1 GiB fixed G1 heap.
CPU affinity does not reserve cores or bind memory.
Both agents load from the same temporary filename, so the artifact path is not
another difference between baseline and candidate JVMs.

Each measurement fork has five 5-second warmup iterations and five 5-second
measurement iterations, with one JMH worker. Confirm warmup stability in the
raw logs before interpreting results. Baseline/candidate order alternates
between repetitions. Each repetition also measures a true no-agent control.
No observations are retried or discarded.

Untimed preflights use the logging exporter to require a servlet server span
from each agent. Each benchmark trial checks the expected response before
measurements begin. Smoke and profile runs do not produce timing comparisons.

Set `artifact-run-id` to a previous successful build's workflow run ID to reuse
the exact build artifacts in a separate job. Ref inputs are ignored when
reusing artifacts; the original provenance remains authoritative. This is useful
for independent A/A checks and profiling without rebuilding.

The `servlet-benchmark-results` artifact contains raw JMH JSON and logs,
`forks.csv`, `metadata.json`, and `summary.md`. Profile runs also contain JFR
recordings. The report uses fresh JVM forks as observations, shows spread and
paired deltas, and reports agent overhead relative to the no-agent control.
It does not claim statistical significance. Compare against repeated A/A
observations and confirm candidates in an independent job.

Both artifact types expire after 14 days. Download results needed for longer-term
comparisons. Benchmark jobs do not post comments or update the published
Petclinic results.

Run the driver's tests without Java or a bare-metal runner:

```sh
python -B -m unittest discover -s benchmark-overhead-jmh -p 'test_*.py'
```
