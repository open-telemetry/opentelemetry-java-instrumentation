# Instrumentation testing infrastructure

`:testing:instrumentation-testing` is this repository's shared infrastructure for testing both
javaagent and library instrumentation. It provides JUnit extensions, assertions, and reusable suites
such as the shared HTTP tests. The same support artifact is useful to external javaagent
instrumentation and extension authors; library-mode testing is supported by this artifact as well.

The central projects keep distinct roles:

- `:testing:javaagent-test-exporters` contains in-memory exporters and helpers loaded by the agent.
- `:testing:javaagent-for-testing` is the testing agent distribution that embeds those exporters.
- `:testing:instrumentation-testing` provides the shared test APIs and handles the agent class-loader
  boundary for captured telemetry.
- `:testing:instrumentation-testing:with-shaded-dependencies` is a dependency wrapper for IDEs and
  Gradle substitution. `:testing:shaded-dependencies` produces the relocated third-party dependency
  bundle consumed by that wrapper and the testing support.

## Running javaagent tests

The repository's `otel.javaagent-testing` plugin configures javaagent tests automatically. Outside
the repository, use the support and testing-agent artifacts from the same release:

```kotlin
val testAgent = configurations.create("testAgent") {
  isCanBeResolved = true
  isCanBeConsumed = false
}

dependencies {
  testImplementation("io.opentelemetry.javaagent:opentelemetry-instrumentation-testing:$version")
  testAgent("io.opentelemetry.javaagent:opentelemetry-javaagent-for-testing:$version")
}
```

The test JVM must run with the resolved testing-agent JAR and the instrumentation test JAR as the
agent initializer:

```text
-javaagent:<javaagent-for-testing.jar>
-Dotel.javaagent.experimental.initializer.jar=<instrumentation-test.jar>
```

Adding `opentelemetry-instrumentation-testing` alone does not attach an agent. The
[`examples/distro` testing-agent project](../../examples/distro/testing/agent-for-testing) shows a
consumer setup.

For ordinary tests that need an SDK, in-memory telemetry, and assertions without javaagent
instrumentation, use `io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension` from
`io.opentelemetry:opentelemetry-sdk-testing`.
