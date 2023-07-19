# Running the tests

## Java versions

Open Telemetry Auto Instrumentation's minimal supported version is java 8.
All jar files that we produce, unless noted otherwise, have bytecode
compatible with java 8 runtime. Our test suite is executed against
java 8, all LTS versions and the latest non-LTS version.

Some libraries that we auto-instrument may have higher minimal requirements.
In this case we compile and test corresponding auto-instrumentation with
higher java version as required by library. The resulting classes will have
higher bytecode level, but as it matches library's java version, no runtime
problem arise.

## Instrumentation tests

Executing `./gradlew instrumentation:test` will run tests for all supported
auto-instrumentations using that java version which runs the Gradle build
itself. These tests usually use the minimal supported version of the
instrumented library.

### Executing tests with specific java version

We run all tests on Java 11 by default, along with Java 8 and 15. To run on the later, set the
`testJavaVersion` Gradle property to the desired major version, e.g., `./gradlew test -PtestJavaVersion=8`,
`./gradlew test -PtestJavaVersion=15`. If you don't have a JDK of these versions
installed, Gradle will automatically download it for you.

### Executing tests against the latest versions of libraries under instrumentation

This is done as part of the nightly build in order to catch when a new version of a library is
released that breaks our instrumentation tests.

To run these tests locally, add `-PtestLatestDeps=true` to your existing `gradlew` command line.

### Executing single test

Executing `./gradlew :instrumentation:<INSTRUMENTATION_NAME>:test --tests <GROOVY TEST FILE NAME>` will run only the selected test.

## Smoke tests

The smoke tests are not run as part of a global `test` task run since they take a long time and are
not relevant for most contributions. Explicitly specify `:smoke-tests:test` to run them.

If you need to run a specific smoke test suite:

```
./gradlew :smoke-tests:test -PsmokeTestSuite=payara
```

If you are on Windows and you want to run the tests using linux containers:

```
USE_LINUX_CONTAINERS=1 ./gradlew :smoke-tests:test -PsmokeTestSuite=payara
```

# Smoke OpenTelemetry starter tests

Smoke tests for the [OpenTelemetry Spring starter](../../instrumentation/spring/starters/spring-boot-starter/README.md).

You can execute the tests in a JVM (`./gradlew smoke-tests-otel-starter:test`) or as Spring native tests (`./gradlew smoke-tests-otel-starter:nativeTest`).

## GraalVM native test

To execute all the instrumentation tests runnable as GraalVM native executables:

```
./gradlew nativeTest
```

[A Github workflow](../../.github/workflows/native-tests-daily.yml) executes the native tests every day.

## Docker disk space

Some of the instrumentation tests (and all of the smoke tests) spin up docker containers via
[testcontainers](https://www.testcontainers.org/). If you run out of space, you may wish to prune
old containers, images and volumes using `docker system prune --volumes`.
