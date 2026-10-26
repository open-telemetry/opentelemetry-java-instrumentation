# Quarkus database overhead benchmark

This runs Bruno Baptista's
[Quarkus observability benchmark](https://github.com/brunobat/quarkus-observability-perf)
with explicit baseline and candidate Java agent artifacts. The application and
Hyperfoil workload come from commit
`a6be333266d75520429793f7785b393a9dc4599d`, under the Apache-2.0 license.
The downloaded artifacts include the upstream license.

The workload uses Quarkus REST, Hibernate, Jakarta Data, JDBC, and PostgreSQL.
Hyperfoil offers 80% paginated reads and 20% inserts. The application uses
Quarkus `3.24.0.CR1`; Hyperfoil is `0.24`. This is a JVM benchmark, not a native
executable benchmark.

## Running on bare metal

Dispatch **Overhead benchmark (daily)** with a `benchmark` input:

| Input | Purpose |
| --- | --- |
| `quarkus-build` | Build the application and agent artifacts on hosted runners only |
| `quarkus-smoke` | Check application behavior, exported traces, and one short trial per agent/control |
| `quarkus-calibrate` | Test 500, 2,000, 5,000, and 10,000 requests/s with no agent and the baseline |
| `quarkus` | Run alternating paired comparisons and no-agent controls |
| `quarkus-profile` | Collect separate application JFR recordings |

`baseline-ref` and `candidate-ref` select agent commits. Use full commit IDs.
Identical commits produce byte-identical agent artifacts for A/A measurements.
`artifact-run-id` reuses the selected run's `quarkus-benchmark-build` artifact.
When reusing artifacts, ref inputs are ignored and the original build provenance
remains authoritative.

To build a new candidate against an existing baseline, set
`quarkus-workload-run-id` to the original build run and select the new
`candidate-ref`. Set `baseline-ref` to that build's baseline commit.
The build verifies the original checksums and preserves the application,
Hyperfoil, workload, license, toolchain record, and baseline agent bytes.
It records the source build's provenance alongside the new candidate's.
This input is ignored when `artifact-run-id` reuses a complete bundle.

`quarkus-rate` defaults to 2,000 requests/s and is ignored during calibration.
Choose a rate with application and infrastructure headroom before comparing
agents. `quarkus-repetitions` defaults to five paired repetitions. Each repetition
also includes a fresh no-agent trial.

Builds run on hosted runners. Measurements run serially on
`oracle-bare-metal-64cpu-1024gb-x86-64-ubuntu-24`, using a digest-pinned Java 21
container. PostgreSQL 16.15, Hyperfoil, the application, and a small OTLP receiver
run inside the container without host services, SSH, privileged profiling,
or nested Docker.

The runner chooses twelve distinct physical cores on one NUMA node. It gives
two to the application, four to PostgreSQL, four to Hyperfoil, one to the OTLP
receiver, and one to the driver. SMT siblings are excluded. Affinity does not
reserve cores or bind memory. The application's G1 heap is fixed at 512 MiB,
and its active processor count is two.

Hyperfoil's host-wide CPU watchdog is disabled because it also monitors the
application, database, and unassigned host cores. The driver's measurement CPU
guards use each process's assigned cores instead.

Each trial starts a fresh database cluster and JVMs. Application records,
database statistics, and caches are not carried between trials. This does not
flush the shared operating system's filesystem cache. Database durability
settings are PostgreSQL defaults.

The regular measurement uses the upstream five-minute ramp and five-minute
constant-rate phase. The measurement phase waits for the ramp's requests to
finish. Smoke uses 5/10 seconds, calibration uses 30/60 seconds, and profiling
uses 300/120 seconds. The generated YAML is saved with each trial.

## Telemetry and correctness

The fixed application build disables Quarkus OpenTelemetry, Micrometer, and
the datasource's library telemetry. Both agents instrument the same application
bytes. The no-agent control runs those bytes without `-javaagent`.

Instrumented trials use 10% trace-ID-ratio sampling, a 512-span export batch,
a 2,048-span queue, and a one-second export interval. Metrics and logs exporters
are disabled. Traces are exported using OTLP HTTP/protobuf to a non-persisting
receiver. Unlike an exporters-disabled test, this includes SDK span-data
conversion, serialization, batching, and export.

Before load generation, each trial inserts records and reads them back.
After load generation, its database row count must match the successful inserts,
including the preflight and warmup.
Instrumented trials must export exactly one HTTP server span for each
deterministically sampled preflight trace, preserve its remote parent, and
attach PostgreSQL client spans beneath it. A no-agent trial must export no
spans. The receiver retains bounded preflight details and aggregate counts,
not all measured spans.
Preflight details include typed OTLP attributes, full span records, scope and
resource records, and schema URLs for telemetry comparisons.

The runner also checks approximate 10% server-span counts under load, receiver
decode errors, and agent export-failure messages. These checks do not replace
full telemetry equivalence checks when accepting an optimization.

## Interpreting observations

The primary metric is application process-tree CPU time per successful request.
CPU is sampled from `/proc` and interpolated at Hyperfoil's measurement phase
boundaries. It excludes PostgreSQL, load-generation, and receiver CPU. Raw
observations include their CPU and memory usage separately, per-operation
latency statistics and histograms, GC logs, database row counts, and telemetry
counts. Profiles are collected in separate jobs, not timing comparisons.

The receiver does not measure Collector processing, persistent storage, or
Jaeger query costs. This setup is intended to compare Java agent versions,
not Quarkus's telemetry extension against the agent.

Trials with failed requests, connection blocking, insufficient offered load,
excessive drain time, export failures, or infrastructure saturation cannot be
used as overhead comparisons. Calibration retains invalid trials as diagnostics.
Other modes stop on an invalid trial. No observations are retried or discarded.
The offered-load guard allows variation in Hyperfoil's random arrivals.

Use fresh trials as statistical observations, not individual requests.
Establish repeatability with at least five A/A pairs before interpreting small
candidate differences, and independently confirm promising changes.

## Artifacts and driver tests

The `quarkus-benchmark-results` artifact contains `quarkus-results.tar.gz`.
The archive preserves Hyperfoil filenames that GitHub's artifact service
does not accept directly. Extract it on a filesystem that supports colons.
It includes `metadata.json`, `trials.json`,
`summary.md`, commands, logs, generated workloads, process CPU/RSS samples, raw
Hyperfoil JSON, preflight traces, and telemetry counts. Profile jobs also
include JFR recordings. Build provenance and hashes identify the application,
agents, upstream workload, Hyperfoil distribution, driver, and receiver.
PostgreSQL's temporary data directories are removed after each trial.

Build and result artifacts expire after 14 days. Download evidence needed for
longer-term comparisons. These jobs do not publish Petclinic results, file
issues, or post GitHub comments.

Install the pinned Python dependencies into an isolated environment, then run:

```sh
python -m pip install -r benchmark-overhead-quarkus/requirements.txt
python -B -m unittest discover -s benchmark-overhead-quarkus -p 'test_*.py'
```
