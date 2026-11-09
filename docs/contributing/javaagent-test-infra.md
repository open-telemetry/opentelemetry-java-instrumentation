# Javaagent instrumentation testing

Javaagent instrumentation tests use a fully shaded `-javaagent` to exercise the same bytecode
instrumentation as when the agent runs against a normal application.

## Testing components

- `:testing:javaagent-test-exporters` contains in-memory exporters and helpers loaded by the agent.
- `:testing:javaagent-for-testing` is the testing agent distribution that embeds those exporters.
- `:testing:testing-common` provides shared test APIs and handles the agent class-loader boundary
  for captured telemetry.
- `:testing:testing-common:with-shaded-dependencies` is a dependency wrapper for IDEs and
  Gradle substitution. `:testing:shaded-dependencies` produces the relocated third-party dependency
  bundle consumed by that wrapper and the testing support.

Metric exporters use delta-preferred aggregation temporality by default.

The exporters and their captured telemetry live in the `AgentClassLoader`. They store telemetry as
OTLP protobuf messages, which can be serialized into byte arrays and passed across the class-loader
boundary. `:testing:testing-common` accesses the exporters using reflection and deserializes the
messages in the test class loader, hiding this complexity from test authors.

## Running javaagent tests

The repository's `otel.javaagent-testing` convention plugin shades the instrumentation and
configures the test JVM arguments:

```text
-javaagent:<javaagent-for-testing.jar>
-Dotel.javaagent.experimental.initializer.jar=<shaded-instrumentation.jar>
```

The initializer JAR contains the instrumentation and its dependencies, shaded with the same package
relocations as the agent. Loading it into the `AgentClassLoader` lets tests run instrumentation
changes without rebuilding the testing-agent distribution.

See the [javaagent testing convention plugin](../../conventions/src/main/kotlin/io.opentelemetry.instrumentation.javaagent-testing.gradle.kts)
for the shading and JVM argument wiring.
