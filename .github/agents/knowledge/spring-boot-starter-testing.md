# [Testing] Spring Boot Starter Test Locations

## Quick Reference

- Use when: locating or adding tests for `opentelemetry-spring-boot-starter` /
  `OpenTelemetryAutoConfiguration` behavior, or judging whether smoke-test coverage exists for it
- Review focus: don't conclude "no smoke tests exist" from `smoke-tests/` alone — the
  library-mode starter has its own top-level module

## Two Separate Spring Smoke-Test Layers

This repo has two unrelated things that both look like "the Spring smoke test":

- **`smoke-tests/images/spring-boot`** — a plain Spring MVC app image used by the
  **javaagent** smoke tests (`smoke-tests/src/test`). It exercises bytecode instrumentation via
  `-javaagent`, not the `opentelemetry-spring-boot-starter` library. `OpenTelemetryAutoConfiguration`
  is not on its classpath.
- **`smoke-tests-otel-starter/*`** — the actual smoke tests for the library-mode starter
  (`opentelemetry-spring-boot-starter`, i.e. `OpenTelemetryAutoConfiguration` and friends). Modules:
  `spring-boot-2`, `spring-boot-3`, `spring-boot-3.2`, `spring-boot-4` (per-Spring-Boot-version test
  suites), `spring-boot-common` (shared abstract test base classes and app), `spring-boot-reactive-*`
  (WebFlux variant), and `spring-smoke-testing` (shared runner/assertion infra used across all of
  the above).

Before reporting a smoke-test gap for the starter, check `smoke-tests-otel-starter/` — not just
`smoke-tests/`.

## Where Starter Tests Live, By Layer

- **Unit-level `ApplicationContextRunner` tests**: in
  `instrumentation/spring/spring-boot-autoconfigure/src/test` — fast, mock the Spring context, good
  for asserting individual bean wiring/conditionals (`@ConditionalOnMissingBean`, etc.) without a
  real server or exporters.
- **Full-stack smoke tests**: in `smoke-tests-otel-starter/spring-boot-*` — real
  `@SpringBootTest` with `RANDOM_PORT`, real HTTP calls, and real (in-memory) OTLP export
  assertions via `AbstractOtelSpringStarterSmokeTest` (shared logic) plus per-version test classes.
  Use these to confirm a bean actually produces working telemetry end-to-end, not just that it's
  present in the context.

## Declarative-Config-Mode Tests Use a Separate Source Set

Both layers use the same pattern for testing declarative config mode (`otel.file_format` set):
a dedicated Gradle source set named `testDeclarativeConfig`, not a flag on the normal `test` source
set. For example:

- `instrumentation/spring/spring-boot-autoconfigure/src/testDeclarativeConfig/`
- `smoke-tests-otel-starter/spring-boot-2/src/testDeclarativeConfig/`

Each includes its own `application.yaml` under `src/testDeclarativeConfig/resources` with
`otel.file_format: "1.0"` set, since `DeclarativeConfigEnabled`/`DeclarativeConfigDisabled` toggle
purely on whether that property is present (see `EarlyConfig.isDeclarativeConfig`). When adding a
test that needs to run under both modes, add it in both the normal source set and the
`testDeclarativeConfig` source set rather than trying to parametrize one test class over both.
