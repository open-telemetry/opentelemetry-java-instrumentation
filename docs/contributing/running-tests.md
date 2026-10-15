# Running the tests

## Java versions

OpenTelemetry Auto Instrumentation's minimal supported version is java 8.
All jar files that we produce, unless noted otherwise, have bytecode
compatibility with the java 8 runtime. Our test suite is executed against
java 8, all LTS versions and the latest non-LTS version.

Some libraries that we auto-instrument may have higher minimal requirements.
In these cases, we compile and test the corresponding auto-instrumentation with
higher java versions as required by the libraries. The resulting classes will
have a higher bytecode level, but since it will match the library's java version,
no runtime problems arise.

## Instrumentation tests

Executing `./gradlew instrumentation:test` will run tests for all supported
auto-instrumentations using that java version which runs the Gradle build
itself. These tests usually use the minimal supported version of the
instrumented library.

### Executing tests with specific java version

We run all tests on `Java 25` by default, along with Java 8, 11, 17, 21, and
27. To run on a specific version, set the `testJavaVersion` gradle property to
the desired major version, e.g., `./gradlew test -PtestJavaVersion=8` or
`./gradlew test -PtestJavaVersion=27`.
If you don't have a JDK of these versions installed, Gradle will automatically download
it for you.

### Executing tests against the latest versions of libraries under instrumentation

This is done as part of the nightly build in order to catch when a new version of a library is
released that breaks our instrumentation tests.

To run these tests locally, add `-PtestLatestDeps=true` to your existing `gradlew` command line.

### Executing single test

Executing `./gradlew :instrumentation:<INSTRUMENTATION_NAME>:test --tests <TEST FILE NAME>` will run only the selected test.

### Suite-local test executions (proof of concept)

Projects using `otel.java-conventions` can opt a `JvmTestSuite` into shared execution
configuration. The suite still owns sources, dependencies, and compilation; executions reuse its
compiled classes and runtime classpath.

```kotlin
import io.opentelemetry.instrumentation.gradle.executions
import io.opentelemetry.instrumentation.gradle.telemetryConfig

testing {
  suites {
    named<JvmTestSuite>("test") {
      executions {
        configureEach {
          jvmArgs("-Djava.net.preferIPv4Stack=true")
        }
        register("Experimental") {
          telemetryConfig("otel.instrumentation.grpc.experimental-span-attributes", "true")
        }
      }
    }
  }
}
```

Declare one `executions` block per suite. `configureEach` configures the existing default
task (`test` here) and each explicitly registered variant (`testExperimental` here).
All common configuration runs before variant configuration, regardless of its position in the
block. Both default and variant tasks are included in `check`. Variants apply only to their
declaring suite; no additional source sets or default tasks are created.

The configuration blocks accept ordinary `Test` configuration, including filters, services,
classpath filtering, and JVM settings. Shared conventions still configure each task independently;
settings are never copied from the default task. Put settings shared by a suite's executions in
`configureEach`, not only in `tasks.test`.

`telemetryConfig(key, value)` sets a test JVM system property and adds its `key=value` condition to
`metadataConfig`. Common and variant conditions compose; a variant value for the same key wins.
Use this helper instead of setting `metadataConfig` manually for these executions. Ordinary
`systemProperty` and `jvmArgs` settings do not add metadata conditions. A default execution without
telemetry conditions leaves `metadataConfig` unset.

The conventions regression tests run in the common CI workflow. To run them locally, use
`./gradlew -p conventions test -Pkotlin.daemon.jvmargs=-Xmx2g`.

### How to prevent linting and formatting warnings from failing tests

During local development, you may want to ignore lint warnings when running tests.

To ignore warnings, formatting issues, or other non-fatal issues in tests, use

```
./gradlew test -Ddev=true -x spotlessCheck -x checkstyleMain
```

The `dev` flag will ignore warnings in tests.

## Smoke tests

The smoke tests are not run as part of a global `test` task since they take a long time and are
not relevant for most contributions. Explicitly specify `:smoke-tests:test` to run them.

If you need to run a specific smoke test suite:

```
./gradlew :smoke-tests:test -PsmokeTestSuite=payara
```

If you are on Windows and you want to run the tests using linux containers:

```
USE_LINUX_CONTAINERS=1 ./gradlew :smoke-tests:test -PsmokeTestSuite=payara
```

If you want to run a specific smoke test:

```
./gradlew :smoke-tests:test --tests '*SpringBootSmokeTest*'
```

## OpenTelemetry starter smoke tests

Smoke tests for the [OpenTelemetry Spring starter](../../instrumentation/spring/starters/spring-boot-starter/README.md)
can be executed in a JVM (`./gradlew smoke-tests-otel-starter:test`) or as Spring Native tests (`./gradlew smoke-tests-otel-starter:nativeTest`).

## GraalVM native test

To execute all the instrumentation tests runnable as GraalVM native executables:

```
./gradlew nativeTest
```

Native test executables use GraalVM's quick-build mode (`-Ob`) to reduce compilation time in CI
and local runs. This setting applies only to test executables.

CI runs native tests only on Oracle GraalVM 25.0 LTS, using the latest available patch release.
[A Github workflow](../../.github/workflows/native-tests-daily.yml) executes the native tests every day.

## Docker disk space

Some of the instrumentation tests (and all of the smoke tests) spin up docker containers via
[testcontainers](https://www.testcontainers.org/). If you run out of space, you may wish to prune
old containers, images and volumes using `docker system prune --volumes`.

## Configuring Docker alternative

For some container environments, such as rootless Podman or a remotely hosted Docker,
testcontainers may need additional configuration to successfully run the tests.
The following environment variables can be used for configuration:

- `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` - The location of the Docker socket on the host. Default is `/var/run/docker.sock`
- `TESTCONTAINERS_HOST_OVERRIDE` - The hostname used for container-to-container communication. Default Docker is `localhost`, but rootless Podman uses `host.containers.internal`

## Troubleshooting CI Test Failures

See [Troubleshooting CI Test Failures](../../CONTRIBUTING.md#troubleshooting-pr-build-failures) for common issues and solutions.

## Debugging

For information on debugging tests or instrumentation, see [Debugging](debugging.md).
