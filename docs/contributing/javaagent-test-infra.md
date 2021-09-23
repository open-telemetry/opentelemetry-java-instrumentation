# Understanding the javaagent instrumentation testing components

Javaagent instrumentation tests are run using a fully shaded `-javaagent` in order to perform
the same bytecode instrumentation as when the agent is run against a normal app.

There are a few key components that make this possible, described below.

## gradle/instrumentation.gradle

* shades the instrumentation
* adds jvm args to the test configuration
  * -javaagent:[agent for testing]
  * -Dotel.javaagent.experimental.initializer.jar=[shaded instrumentation jar]

The `otel.javaagent.experimental.initializer.jar` property is used to load the shaded instrumentation jar into the
`AgentClassLoader`, so that the javaagent jar doesn't need to be re-built each time.

## :testing:agent-exporter

This contains the span and metric exporters that are used.

These are in-memory exporters, so that the tests can verify the spans and metrics being exported.

These exporters and the in-memory data live in the `AgentClassLoader`, so tests must access them
using reflection. To simplify this, they store the in-memory data using the OTLP protobuf objects,
so that they can be serialized into byte arrays inside the `AgentClassLoader`, then passed back
to the tests and deserialized inside their class loader where they can be verified. The
`:testing-common` module (described below) hides this complexity from instrumentation test authors.

## :agent-for-testing

This is a custom distro of the javaagent that embeds the `:testing:agent-exporter`.

## :testing-common

This module provides methods to help verify the span and metric data produced by the
instrumentation, hiding the complexity of accessing the in-memory exporters that live in the
`AgentClassLoader`.
