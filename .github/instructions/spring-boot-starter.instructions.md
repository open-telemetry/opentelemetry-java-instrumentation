---
applyTo: "instrumentation/spring/**/*,smoke-tests-otel-starter/**/*,smoke-tests/images/spring-boot/**/*,smoke-tests/src/test/**/*"
---

# Spring Boot starter testing

Apply these rules to coverage for the library-mode `opentelemetry-spring-boot-starter`
and `OpenTelemetryAutoConfiguration`.

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
- Use the starter smoke tests when coverage must establish that wiring produces working telemetry,
  not merely that a bean exists.

## Declarative mode

- Run flat and declarative starter smoke tests in the default `test` source set. Use
  `@ActiveProfiles("declarative")` to load `application-declarative.yaml` and deactivate the
  shared flat settings. Spring can then discover both contexts during native test AOT
  processing. Use a separate fixture for startup cases that need different configuration.
- Keep declarative autoconfigure tests in `testDeclarativeConfig` with their own `application.yaml`.
- `EarlyConfig.isDeclarativeConfig` selects the mode at startup based on `otel.file_format`.
  Assert that the declarative configuration took effect, for example by checking a service name
  set in YAML. Equivalent flat and declarative settings do not need duplicate behavior matrices.
