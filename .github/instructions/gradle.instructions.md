---
applyTo: "**/*.gradle.kts"
---

# Gradle Rules (first-pass review)

## [Build] Javaagent convention dependencies

- Projects applying `io.opentelemetry.instrumentation.javaagent-testing`,
  `io.opentelemetry.instrumentation.javaagent-instrumentation`, `otel.javaagent-testing`, or
  `otel.javaagent-instrumentation` must not redeclare an exact dependency supplied by that plugin or
  its convention chain in the same configuration.
- Treat project dependencies as equivalent when `otel.java-conventions` dependency substitution
  maps the convention's external module to that project.
- Preserve different configurations and custom JVM suites, Testcontainers feature modules,
  test-only compile dependencies for upstream API signatures, and declarations with version
  constraints, strict pins, excludes, classifiers, capabilities, or non-default project
  configurations. Do not apply this rule to projects that only apply `otel.java-conventions`.
- Prefer repository Gradle enforcement when available and do not duplicate its CI diagnostics in
  review. Resolved dependency reports alone do not prove that declarations are non-duplicative.
