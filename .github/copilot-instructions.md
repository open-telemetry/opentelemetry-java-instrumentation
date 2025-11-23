# OpenTelemetry Java Instrumentation

OpenTelemetry Java Instrumentation is a Java agent that provides automatic instrumentation for a wide variety of libraries and frameworks. It dynamically injects bytecode to capture telemetry data without requiring code changes.

Always reference these instructions first and fallback to search or bash commands only when you encounter unexpected information that does not match the info here.

## General notes

- Use Java 21 for building (required, see `.java-version`)
  - `./gradlew assemble` -- builds the complete Java agent. NEVER CANCEL. Set timeout to 60+ minutes.
  - The Java agent artifact will be at: `javaagent/build/libs/opentelemetry-javaagent-<version>.jar`
- Code quality:
  - `./gradlew spotlessCheck` -- NEVER CANCEL. ~500 modules. Set timeout to 30+ minutes.
  - `./gradlew spotlessApply` -- auto-fix formatting issues
  - `./gradlew generateLicenseReport --no-build-cache` -- update license information
- Docker is required for some tests (smoke tests, instrumentation tests using testcontainers)
- Node 16 for Vaadin tests (if working on Vaadin instrumentation)


## Internal docs

The following documentation should be referenced:

### Core Development Guides

- `docs/contributing/writing-instrumentation.md` - Complete guide for writing new instrumentation (library and javaagent)
- `docs/contributing/using-instrumenter-api.md` - Detailed documentation on the Instrumenter API (core to all instrumentation)
- `docs/contributing/writing-instrumentation-module.md` - How to implement InstrumentationModule classes
- `docs/contributing/style-guideline.md` - Code style, formatting rules, and conventions (Google Java Style Guide)

### Architecture & Structure

- `docs/contributing/javaagent-structure.md` - Class loader architecture and javaagent internal structure
- `docs/contributing/muzzle.md` - Safety mechanism that prevents instrumentation mismatches
- `docs/contributing/javaagent-test-infra.md` - Testing infrastructure and patterns

### Testing & Debugging

- `docs/contributing/running-tests.md` - How to run tests across different Java versions and configurations
- `docs/contributing/debugging.md` - Debugging instrumentation and tests

### Documentation & Project Scope

- `docs/contributing/documenting-instrumentation.md` - How to document instrumentation (metadata.yaml, telemetry collection)
- `docs/supported-libraries.md` - List of supported libraries and frameworks
- `docs/instrumentation-list.yaml` - Generated comprehensive instrumentation reference (DO NOT edit manually)

### Configuration & Features

- `docs/logger-mdc-instrumentation.md` - MDC (Mapped Diagnostic Context) instrumentation details

## Key Files to Check After Changes

- Always run `./gradlew spotlessCheck` before committing or the CI will fail
- Update supported libraries docs: `docs/supported-libraries.md`
- Update metadata.yaml file if applicable (see `docs/contributing/documenting-instrumentation.md`)

## Repository Structure

Key directories:

- `instrumentation/` - Contains all instrumentation modules (modules for different libraries)
- `javaagent/` - Core Java agent implementation
- `docs/` - Documentation including supported libraries list
- `smoke-tests/` - End-to-end integration tests
- `examples/` - Example extensions and distributions
- `gradle-plugins/` - Custom Gradle plugins used by the build
- `.github/workflows/` - CI/CD pipeline definitions
- `settings.gradle.kts` - Contains all module definitions

## Testing

### Commands

- `./gradlew test` -- runs tests. NEVER CANCEL. Set timeout to 180+ minutes.
- `./gradlew check` -- runs all tests and checks. NEVER CANCEL. Set timeout to 180+ minutes.
- `./gradlew :smoke-tests:test` -- runs smoke tests (separate from main test suite)
- `./gradlew test -PtestJavaVersion=<version>` -- test on specific Java version (8, 11, 17, 21, 23, 24, 25-ea)
- `./gradlew test -PtestLatestDeps=true` -- test against latest dependency versions

### Guidelines

Tests use AssertJ for assertions and JUnit 5 as the testing framework

Test classes and methods should not be public

When registering tests in gradle configurations, if using `val testName by registering(Test::class) {`...
then you need to include `testClassesDirs` and `classpath` like so:

```
val testExperimental by registering(Test::class) {
  testClassesDirs = sourceSets.test.get().output.classesDirs
  classpath = sourceSets.test.get().runtimeClasspath
  ...
}
```

## General Java guidelines

* Always import classes when possible (i.e. don't use fully qualified class names in code).

## Gradle CLI

Never use the `--rerun-tasks` flag unless explicitly asked to use this option.

Gradle automatically detects changes and re-runs tasks automatically when needed. Using `--rerun-tasks`
is wasteful and slows down builds unnecessarily.

## Throwing exceptions

When writing instrumentation, you have to be really careful about throwing exceptions. For library
instrumentations it might be acceptable, but in javaagent code you shouldn't throw exceptions
(keep in mind that javaagent instrumentations sometimes use library instrumentations).

In javaagent instrumentations we try not to break applications. If there are changes in the instrumented
library that are not compatible with the instrumentation we disable the instrumentation instead of letting
it fail. This is handled by muzzle. In javaagent instrumentations you should not fail if the methods
that you need don't exist.

## Javaagent Instrumentation

### Java8BytecodeBridge

When to use `Java8BytecodeBridge.currentContext()` vs `Context.current()` ?

Using `Context.current()` is preferred. `Java8BytecodeBridge.currentContext()` is for using inside
advice. We need this method because advice code is inlined in the instrumented method as it is.
Since `Context.current()` is a static interface method it will cause a bytecode verification error
when it is inserted into a pre 8 class. `Java8BytecodeBridge.currentContext()` is a regular class
static method and can be used in any class version.
