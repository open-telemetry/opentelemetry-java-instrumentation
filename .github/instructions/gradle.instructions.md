---
applyTo: "**/*.gradle.kts"
---

# Gradle Rules (first-pass review)

## [Build] Javaagent testing convention dependencies

- Projects applying `io.opentelemetry.instrumentation.javaagent-testing` directly, or through
  `otel.javaagent-testing` or `otel.javaagent-instrumentation`, must not redeclare
  `io.opentelemetry.javaagent:opentelemetry-testing-common` in a JVM test suite. The convention
  supplies it to every JVM test suite.
- Projects applying `otel.javaagent-testing` directly or through
  `otel.javaagent-instrumentation` must not redeclare the exact base
  `org.testcontainers:testcontainers` artifact in `testImplementation`. The convention supplies it
  to the default test suite.
- Do not apply these rules to projects that do not apply the providing convention. Do not remove
  Testcontainers feature modules such as `testcontainers-junit-jupiter` or
  `testcontainers-cassandra`; they are separate dependencies. A custom test suite may still need
  its own base Testcontainers dependency because the convention adds it only to the default suite.
