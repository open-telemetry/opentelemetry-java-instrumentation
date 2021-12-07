
# Overhead tests

* [Process](#process)
* [What do we measure?](#what-do-we-measure)
* [Config](#config)
  + [Agents](#agents)
* [Automation](#automation)
* [Setup and Usage](#setup-and-usage)
* [Visualization](#visualization)

This directory will contain tools and utilities
that help us to measure the performance overhead introduced by 
the agent and to measure how this overhead changes over time.

The overhead tests here should be considered a "macro" benchmark. They serve to measure high-level
overhead as perceived by the operator of a "typical" application. Tests are performed on a Java 11
distribution from [AdoptOpenJDK](https://adoptopenjdk.net/).

## Process

There is one dynamic test here called [OverheadTests](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/benchmark-overhead/src/test/java/io/opentelemetry/OverheadTests.java). 
The `@TestFactory` method creates a test pass for each of the [defined configurations](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/benchmark-overhead/src/test/java/io/opentelemetry/config/Configs.java).
Before the tests run, a single collector instance is started. Each test pass has one or more agents configured and those are tested in series.
For each agent defined in a configuration, the test runner (using [testcontainers](https://www.testcontainers.org/)) will:
1. create a fresh postgres instance and populate it with initial data.
2. create a fresh instance of [spring-petclinic-rest](https://github.com/spring-petclinic/spring-petclinic-rest) instrumented with the specified agent
3. measure the time until the petclinic app is marked "healthy" and then write it to a file
4. if configured, perform a warmup phase. During the warmup phase, a bit of traffic is generated in order to get the application into a steady state (primarily helping facilitate jit compilations). Currently, we use a 30 second warmup time. 
5. start a JFR recording by running `jcmd` inside the petclinic container
6. run the [k6 test script](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/benchmark-overhead/k6/basic.js) with the configured number of iterations through the file and the configured number of concurrent virtual users (VUs).
7. after k6 completes, petclinic is shut down
8. after petclinic is shut down, postgres is shut down

And this repeats for every agent configured in each test configuration.

After all the tests are complete, the results are collected and commited back to the `/results` subdirectory as csv and summary text files.

## What do we measure?

For each test pass, we record the following metrics in order to compare agents and determine
relative overhead.

| metric name              | units      | description                                                     |
|--------------------------|------------|----------------------------------------------------------|
| Startup time             | ms         | how long it takes for the spring app to report "healthy"
| Total allocated mem      | bytes      | across the life of the application
| Heap (min)               | bytes      | smallest observed heap size
| Heap (max)               | bytes      | largest observed heap size
| Thread switch rate       | # / s      | max observed thread context switch rate
| GC time                  | ms         | total amount of time spent paused for garbage collection
| Request mean             | ms         | average time to handle a single web request (measured at the caller)
| Request p95              | ms         | 95th percentile time to handle a single web requ4st (measured at the caller)
| Iteration mean           | ms         | average time to do a single pass through the k6 test script
| Iteration p95            | ms         | 95th percentile time to do a single pass through the k6 test script
| Peak threads             | #          | Highest number of running threads in the VM, including agent threads
| Network read mean        | bits/s     | Average network read rate
| Network write mean       | bits/s     | Average network write rate
| Average JVM user CPU     | %          | Average observed user CPU (range 0.0-1.0)
| Max JVM user CPU         | %          | Max observed user CPU used (range 0.0-1.0) 
| Average machine tot. CPU | %          | Average percentage of machine CPU used (range 0.0-1.0) 
| Total GC pause nanos     | ns         | JVM time spent paused due to GC 
| Run duration ms          | ms         | Duration of the test run, in ms

## Config

Each config contains the following:
* name
* description
* list of agents (see below)
* maxRequestRate (optional, used to throttle traffic)
* concurrentConnections (number of concurrent virtual users [VUs])
* totalIterations - the number of passes to make through the k6 test script
* warmupSeconds - how long to wait before starting conducting measurements

Currently, we test:
* no agent versus latest released agent
* no agent versus latest snapshot 
* latest release vs. latest snapshot

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