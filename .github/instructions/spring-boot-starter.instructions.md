---
applyTo: "instrumentation/spring/**/*,smoke-tests-otel-starter/**/*,smoke-tests/images/spring-boot/**/*,smoke-tests/src/test/**/*"
---

# Spring Boot starter testing

Apply these rules when adding or evaluating coverage for the library-mode
`opentelemetry-spring-boot-starter` and `OpenTelemetryAutoConfiguration`.

## Test layers

- `instrumentation/spring/spring-boot-autoconfigure/src/test` contains
  `ApplicationContextRunner` tests for bean wiring and conditionals without a real server.
- `smoke-tests-otel-starter/spring-boot-*` contains full-stack starter tests with real
  `@SpringBootTest` applications, random ports, HTTP calls, and in-memory OTLP assertions.
  Shared logic lives in `spring-boot-common` and `spring-smoke-testing`; version-specific
  and reactive modules exercise their corresponding runtimes.
- `smoke-tests/images/spring-boot` is a javaagent application image used by
  `smoke-tests/src/test`. It does not exercise the starter or put
  `OpenTelemetryAutoConfiguration` on its classpath.

Use the starter smoke tests when coverage must establish that wiring produces working telemetry,
not merely that a bean exists.

## Declarative mode

Both starter test layers use a separate `testDeclarativeConfig` source set with its own
`resources/application.yaml` containing `otel.file_format: "1.0"`. Mode selection follows
`EarlyConfig.isDeclarativeConfig`, not an in-test flag.

For changed mode-specific wiring that needs both modes, add coverage in the normal and declarative
source sets rather than parameterizing one class over a mode selected during initialization.
This does not require duplicate behavior matrices for equivalent flat/YAML settings.
