# Understanding the javaagent instrumentation testing components

Javaagent instrumentation tests are run using a fully shaded `-javaagent` in order to perform
the same bytecode instrumentation as when the agent is run against a normal app.

The combined `:testing-common` module is published as
`io.opentelemetry.javaagent:opentelemetry-javaagent-instrumentation-testing`. It provides this
repository's instrumentation testing infrastructure, including reusable HTTP suites,
instrumentation-scope-version checks, telemetry metadata collection, and shared-suite execution
against agent and library implementations. `LibraryInstrumentationExtension` runs the library side
of these shared tests without attaching an agent.

External Javaagent instrumentation and extension tests can also use this artifact. For ordinary
tests needing an SDK, in-memory telemetry, and assertions instead, use
`io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension` from
`io.opentelemetry:opentelemetry-sdk-testing`.

## Agent test setup

Use `io.opentelemetry.javaagent:opentelemetry-javaagent-instrumentation-testing:<version>` as a test
dependency and obtain `io.opentelemetry.javaagent:opentelemetry-agent-for-testing:<version>`.
The testing agent and testing dependencies must come from matching releases (including the alpha
version suffix). These are alpha artifacts subject to the repository's [versioning policy](../../VERSIONING.md).
The test-support dependency alone does not attach the agent.

Start the test JVM with:

```text
-javaagent:/path/to/opentelemetry-agent-for-testing-<version>.jar
-Dotel.javaagent.experimental.initializer.jar=/path/to/shaded-instrumentation.jar
```

The initializer JAR contains the instrumentation under test and its service registrations, shaded
as in the [distro example](../../examples/distro). The initializer property loads it into the
`AgentClassLoader`, so the testing agent does not need rebuilding for each instrumentation change.
Register `AgentInstrumentationExtension` in the test to access exported telemetry.

In this repository, `otel.javaagent-instrumentation` applies
[`io.opentelemetry.instrumentation.javaagent-testing`](../../conventions/src/main/kotlin/io.opentelemetry.instrumentation.javaagent-testing.gradle.kts),
which shades the instrumentation and supplies the testing dependencies and JVM arguments.
For external extension setup, see the distro example's
[testing convention](../../examples/distro/buildSrc/src/main/kotlin/otel.instrumentation-conventions.gradle.kts).

## :testing:agent-exporter

This contains the in-memory telemetry exporters used by agent tests.

The exporters and their data live in the `AgentClassLoader`; `:testing-common` makes the data
available to assertions in the test class loader.

## :testing:agent-for-testing

This is a custom distro of the javaagent that embeds the `:testing:agent-exporter`.

## :testing-common

This module provides the JUnit extensions and shared instrumentation test suites. See
[writing instrumentation tests](writing-instrumentation.md#writing-instrumentation-tests) for
agent/library shared-suite usage.
