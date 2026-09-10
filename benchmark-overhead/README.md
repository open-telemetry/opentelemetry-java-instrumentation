# Overhead tests

- [Process](#process)
- [What do we measure?](#what-do-we-measure)
- [Config](#config)
- [Agents](#agents)
- [Automation](#automation)
- [Setup and Usage](#setup-and-usage)
- [Visualization](#visualization)
- [JDK 25 AOT startup comparison](#jdk-25-aot-startup-comparison)

This directory will contain tools and utilities
that help us to measure the performance overhead introduced by
the agent and to measure how this overhead changes over time.

The overhead tests here should be considered a "macro" benchmark. They serve to measure high-level
overhead as perceived by the operator of a "typical" application. Tests are performed on a Java 11
distribution from [Eclipse Temurin](https://projects.eclipse.org/projects/adoptium.temurin).

## Process

There is one dynamic test here called [OverheadTests](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/benchmark-overhead/src/test/java/io/opentelemetry/OverheadTests.java).
The `@TestFactory` method creates a test pass for each of the [defined configurations](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/benchmark-overhead/src/test/java/io/opentelemetry/config/Configs.java).
Before the tests run, a single collector instance is started. Each test pass has one or more agents configured and those are tested in series.
For each agent defined in a configuration, the test runner (using [testcontainers](https://www.testcontainers.org/)) will:

1. create a fresh postgres instance and populate it with initial data.
2. create a fresh instance of [spring-petclinic-rest](https://github.com/spring-petclinic/spring-petclinic-rest) instrumented with the specified agent
3. measure the time until the petclinic app is marked "healthy" and then write it to a file.
4. if configured, perform a warmup phase. During the warmup phase, a bit of traffic is generated in order to get the application into a steady state (primarily helping facilitate jit compilations). Currently, we use a 30 second warmup time.
5. start a JFR recording by running `jcmd` inside the petclinic container
6. run the [k6 test script](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/benchmark-overhead/k6/basic.js) with the configured number of iterations through the file and the configured number of concurrent virtual users (VUs).
7. after k6 completes, petclinic is shut down
8. after petclinic is shut down, postgres is shut down

And this repeats for every agent configured in each test configuration.

After all the tests are complete, the results are collected and committed back to the `/results` subdirectory as csv and summary text files.

## What do we measure?

For each test pass, we record the following metrics in order to compare agents and determine
relative overhead.

| metric name              | units  | description                                                                  |
| ------------------------ | ------ | ---------------------------------------------------------------------------- |
| Startup time             | ms     | How long it takes for the spring app to report "healthy"                     |
| Total allocated mem      | bytes  | Across the life of the application                                           |
| Heap (min)               | bytes  | Smallest observed heap size                                                  |
| Heap (max)               | bytes  | Largest observed heap size                                                   |
| Thread switch rate       | # / s  | Max observed thread context switch rate                                      |
| GC time                  | ms     | Total amount of time spent paused for garbage collection                     |
| Request mean             | ms     | Average time to handle a single web request (measured at the caller)         |
| Request p95              | ms     | 95th percentile time to handle a single web request (measured at the caller) |
| Iteration mean           | ms     | average time to do a single pass through the k6 test script                  |
| Iteration p95            | ms     | 95th percentile time to do a single pass through the k6 test script          |
| Peak threads             | #      | Highest number of running threads in the VM, including agent threads         |
| Network read mean        | bits/s | Average network read rate                                                    |
| Network write mean       | bits/s | Average network write rate                                                   |
| Average JVM user CPU     | %      | Average observed user CPU (range 0.0-1.0)                                    |
| Max JVM user CPU         | %      | Max observed user CPU used (range 0.0-1.0)                                   |
| Average machine tot. CPU | %      | Average percentage of machine CPU used (range 0.0-1.0)                       |
| Total GC pause nanos     | ns     | JVM time spent paused due to GC                                              |
| Run duration ms          | ms     | Duration of the test run, in ms                                              |

## Config

Each config contains the following:

- name
- description
- list of agents (see below)
- maxRequestRate (optional, used to throttle traffic)
- concurrentConnections (number of concurrent virtual users [VUs])
- totalIterations - the number of passes to make through the k6 test script
- warmupSeconds - how long to wait before starting conducting measurements

Currently, we test:

- no agent versus latest released agent
- no agent versus latest snapshot
- latest release vs. latest snapshot
- latest snapshot with indy enabled

Additional configurations can be created by submitting a PR against the `Configs` class.

### Agents

An agent is defined in code as a name, description, optional URL, and optional additional
arguments to be passed to the JVM (not including `-javaagent:`). New agents may be defined
by creating new instances of the `Agent` class. The `AgentResolver` is used to download
the relevant agent jar for an `Agent` definition.

## Automation

The tests are run nightly via github actions. The results are collected and appended to
a csv file, which is committed back to the repo in the `/results` subdirectory.

## Setup and Usage

The tests require docker to be running. Simply run `OverheadTests` in your IDE.

Alternatively, you can run the tests from
the command line with gradle:

```
cd benchmark-overhead
./gradlew test

```

## Visualization

None yet. Help wanted! Our goal is to have the results and a rich UI running in the
`gh-pages` branch similar to [earlier tools](https://breedx-splk.github.io/iguanodon/web/).
Please help us make this happen.

## JDK 25 AOT startup comparison

The opt-in `aotStartupBenchmark` task compares the existing small Spring smoke application on HotSpot JDK 25 in four configurations: normal and AOT, each with and without the Java agent. It does not run in ordinary tests or the nightly overhead workflow, and has no performance pass/fail thresholds.

Use JDK 21 or newer for Gradle and Docker with Linux containers. The benchmark uses a pinned JDK 25 Spring smoke image, two container CPUs, a 1 GiB container memory limit, and `-Xmx512m`. Run it on an otherwise idle machine.

Build the current agent from the repository root:

```sh
./gradlew :javaagent:shadowJar
```

Then run the standalone benchmark, passing the exact local agent JAR:

```sh
cd benchmark-overhead
./gradlew aotStartupBenchmark \
  -PaotBenchmarkAgentJar=/absolute/path/to/opentelemetry-javaagent-VERSION.jar
```

On Windows, use `.\gradlew.bat` and a quoted Windows path:

```powershell
Set-Location benchmark-overhead
.\gradlew.bat aotStartupBenchmark "-PaotBenchmarkAgentJar=C:\path\to\opentelemetry-javaagent-VERSION.jar"
```

Each invocation collects fresh observations even when compiled classes are up to date. Defaults are two discarded starts and twenty measured starts per variant. For a short functional run, add `-PaotBenchmarkWarmups=0 -PaotBenchmarkSamples=1`. Both options count fresh JVM starts, not warmup within a running JVM.

The benchmark creates separate no-agent and agent-compatible caches without an active agent. It packages the image's exploded classes and resources into a JAR once so all four configurations use the same flat class path. Cache preparation, diagnostic preflight, and image pulls are outside the measurements. Images without the expected flat layout are rejected rather than silently benchmarking a cache of only JDK classes.

Within each pair, only AOT options differ. Both agent configurations preload the agent JAR on the bootstrap class path and add `java.instrument`; the no-agent configurations do neither. The normal agent configuration is therefore the AOT-compatible recipe without a cache, not an ordinary `-javaagent`-only launch. Field injection uses its enabled default. Instrumentation remains active, but traces, metrics, and logs exporters are all `none`; this measures neither telemetry delivery nor collector overhead.

Every AOT sample requires cache use. Separate untimed runs confirm AOT-linked classes, archived application-class loading, and agent transformation. Timed runs omit agent debug, class-load and instrumentation tracing, and expensive cache verification.

Starts run serially in rotating order. Every sample must return HTTP 200 and `Hi!` from `/greeting`; a failure aborts the benchmark and retains diagnostics without producing a comparison. Samples are not retried or filtered as outliers.

Results are written to `build/reports/aot-startup/<run-id>/`:

- `samples.csv` contains discarded and measured observations.
- `metadata.properties` records exact commands, image/JDK identity, agent hash/version, repository state, resource limits, sample counts, and cache preparation costs.
- `summary.md` compares medians and interquartile ranges and calculates absolute and percentage AOT reductions within each pair.
- `startup.svg` is a presentation-ready grouped chart with a shared zero-based axis and interquartile ranges.
- Preparation, preflight, and per-sample logs accompany the report.

The primary metric is **JVM uptime at Spring startup completion**, from Spring's `JVM running for` log field. It includes `premain()` and work before Spring's own timer starts. It is not an isolated agent-installation measurement or an exact OS-process lifetime. Spring initialization and container-to-HTTP time are secondary metrics; the latter includes Docker and harness overhead.

These are fresh-process starts with warmed filesystem/image caches, not cold-machine starts. Results apply to this small Spring workload and the recorded environment. They do not establish throughput, JIT warmup, or the benefit for larger services. Read both seconds saved and percentage reduction: a smaller relative benefit with an agent does not necessarily mean less absolute time saved.

To exercise only the parser, configuration, statistics, and report tests without Docker:

```sh
./gradlew test --tests 'io.opentelemetry.startup.*Test'
```
