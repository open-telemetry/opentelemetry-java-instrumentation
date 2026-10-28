# Understanding the javaagent instrumentation testing components

Javaagent instrumentation tests are run using a fully shaded `-javaagent` in order to perform
the same bytecode instrumentation as when the agent is run against a normal app.

There are a few key components that make this possible, described below.

## gradle/instrumentation.gradle

- shades the instrumentation
- adds jvm args to the test configuration
  - -javaagent:[agent for testing]
  - -Dotel.javaagent.experimental.initializer.jar=[shaded instrumentation jar]

The `otel.javaagent.experimental.initializer.jar` property is used to load the shaded instrumentation jar into the
`AgentClassLoader`, so that the javaagent jar doesn't need to be re-built each time.

## :testing:agent-exporter

This contains the span and metric exporters that are used.

These are in-memory exporters, so that the tests can verify the spans and metrics being exported.

These exporters and the in-memory data live in the `AgentClassLoader`, so tests must access them
using reflection. To simplify this, they store the in-memory data using the OTLP protobuf objects,
so that they can be serialized into byte arrays inside the `AgentClassLoader`, then passed back
to the tests and deserialized inside their class loader where they can be verified. The
`:agent-testing` module (described below) hides this complexity from instrumentation test authors.

## :testing:agent-for-testing

This is a custom distro of the javaagent that embeds the `:testing:agent-exporter`.

## :agent-testing

This module provides the agent test runner, agent JUnit extensions (including the HTTP extensions),
and bridges to the in-memory exporters and listeners that live in the `AgentClassLoader`.
Tests using it must launch with `agent-for-testing`.

For agent instrumentation tests in another repository, add:

```kotlin
testImplementation("io.opentelemetry.javaagent:opentelemetry-javaagent-instrumentation-testing:3.0.0-alpha-SNAPSHOT")
```

This also adds `testing-common` as a dependency, so its shared assertions and HTTP fixtures are
available without a separate dependency declaration.

## :testing-common

This module provides the shared test runner, library JUnit extensions, assertions, HTTP fixtures,
metadata collection, and utilities. Library instrumentation tests use
`io.opentelemetry.javaagent:opentelemetry-testing-common`.
