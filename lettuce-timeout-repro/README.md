# Lettuce reactive GET timeout reproduction

This opt-in harness investigates
[issue #19814](https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/19814).
It does not change or run as part of the main instrumentation build.

## Prerequisites

- Linux, because the Redis and collector containers use Docker host networking.
- Docker with a running daemon, and access to Docker Hub and Maven Central.
- A JDK 21 installation. The standalone Gradle build uses Java 21.

From a clean checkout, run the harness with the Java executable you want it to use:

```sh
JAVA=/path/to/jdk-21/bin/java ./lettuce-timeout-repro/run.sh
```

The first run builds the application jar. The selected executable is also used for that initial
Gradle build, even if `JAVA_HOME` is unset or points to another JDK. To build separately:

```sh
cd lettuce-timeout-repro
JAVA_HOME=/path/to/jdk-21 ./gradlew --no-daemon bootJar
```

The default run starts a three-master Redis cluster, then runs the workload once without an agent
and once each with agents 2.29.0, 2.31.1, and 2.33.0. For example:

```sh
JAVA=/path/to/jdk-21/bin/java CONCURRENCY=1024 ./lettuce-timeout-repro/run.sh
JAVA=/path/to/jdk-21/bin/java CONCURRENCY=1024 RATE=10000 ./lettuce-timeout-repro/run.sh
JAVA=/path/to/jdk-21/bin/java REDIS_IMAGE=redis:8.2.2-alpine \
  AGENTS="none 2.29.0 2.33.0" ./lettuce-timeout-repro/run.sh
```

The script starts a fresh Redis for each agent configuration and a local collector that discards
telemetry. It refuses to start if one of its required host ports is already in use. Redis startup
and cluster-health waits are bounded; `REDIS_STARTUP_TIMEOUT_SECONDS` and
`CLUSTER_READY_TIMEOUT_SECONDS` control their 30-second defaults.

## Parameters

| Environment variable | Default | Meaning |
| --- | --- | --- |
| `JAVA` | `$JAVA_HOME/bin/java` when `JAVA_HOME` is set, otherwise `java` from `PATH` | Java executable for the build, app, and load generator |
| `JAVA_HOME` | Derived from the selected Java executable | Used to select `JAVA` when it is unset; the script sets it to the selected JDK for Gradle |
| `MODE` | `cluster` | `cluster` or `standalone` Redis |
| `AGENTS` | `none 2.29.0 2.31.1 2.33.0` | Space-separated configurations; use `none` or a published agent version |
| `CONCURRENCY` | `256` | Maximum in-flight requests; in closed-loop mode, also the number of workers |
| `RATE` | `0` | `0` uses closed-loop workers; a positive value starts requests at that fixed rate, skipping requests when the in-flight limit is reached or a scheduled task cannot start before the deadline |
| `WARMUP_SECONDS` | `30` | Warmup duration for each configuration; set to `0` to disable |
| `DURATION_SECONDS` | `120` | Measured load duration for each configuration |
| `REDIS_TIMEOUT` | `60s` | Spring Data Redis command timeout |
| `REQUEST_TIMEOUT_SECONDS` | `90` | Maximum time for each load-generator HTTP request |
| `KEY_COUNT` | `10000` | Number of Redis keys populated and read |
| `APP_HEAP` | `512m` | Application JVM initial and maximum heap |
| `REDIS_IMAGE` | `redis:7.4.6-alpine` | Redis Docker image |
| `COLLECTOR_IMAGE` | `otel/opentelemetry-collector-contrib:0.138.0` | Local discard-only collector image |
| `REDIS_STARTUP_TIMEOUT_SECONDS` | `30` | Maximum wait for each Redis node to accept commands |
| `CLUSTER_READY_TIMEOUT_SECONDS` | `30` | Maximum wait for Redis cluster health |

The harness uses host ports 18080 (application), 4317 and 4318 (collector), 17000–17002 and
27000–27002 (cluster), or 16379 (standalone Redis).

## Results

Each invocation writes to a unique `lettuce-timeout-repro/build/results/<timestamp>-<run-id>/`
directory. `summary.md` contains the configuration summary and comparison table. Each agent
subdirectory contains `app.log`, `warmup.txt` when warmup is enabled, `load.txt`, and `stats.json`.

In the measured attempt, all configurations were run on a 4-core Linux host with Java 21 and a
Redis 7.4.6 cluster. Each row had zero failed requests, zero Redis errors, and zero requests taking
at least one second.

| Workload | No agent req/s (p99 ms) | 2.29.0 req/s (p99 ms) | 2.31.1 req/s (p99 ms) | 2.33.0 req/s (p99 ms) |
| --- | ---: | ---: | ---: | ---: |
| 256 concurrent requests | 17,968 (34) | 14,127 (43) | 14,845 (40) | 14,462 (42) |
| 1,024 concurrent requests | 17,514 (123) | 13,518 (149) | 13,367 (154) | 13,925 (148) |
| 10,000 requests/s, at most 1,024 in flight | 9,922 (94) | 9,870 (136) | 9,872 (138) | 9,825 (154) |

In the fixed-rate workload, 0.7–1.7% of requests were skipped in every configuration, including
without an agent. The agent configurations used about 20–25% less capacity than no agent, but
2.31.1 and 2.33.0 were not measurably more expensive than 2.29.0 in these runs.

**No tested configuration reproduced the reported issue**, including the reported-affected 2.31.1.
These results do not establish that the issue is fixed; the reported conditions may not have been
recreated.

## What was and was not tested

The measured comparisons used Redis 7.4.6 cluster mode and the listed three workloads. A Redis
8.2.2 cluster run completed, but its results were not collected. Standalone mode was not tested.
Other untested scenarios include an unavailable collector, topology changes, MOVING/MIGRATING
maintenance events, managed Redis services, and production-scale multi-host load. The attempt ran
on one 4-core host.

Separately, with agent 2.33.0, a 3-second Redis timeout, and one paused cluster node, the harness
did exercise the timeout stack through `RedisPublisher`, `ClusterCommand.completeExceptionally`,
`MaintenanceAwareExpiryWriter`, and the agent's reactor `TracingSubscriber`. This was an
artificially induced timeout-path check, not a reproduction of the reported regression.
