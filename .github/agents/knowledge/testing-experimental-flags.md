# [Testing] Experimental Feature Flag Tests

Consult this article when adding experimental-attribute coverage or moving a
flag out of the default test task. It shows how to wire `testExperimental` and
assert both flag modes.

## What `testExperimental` Is For

Some instrumentation modules support extra attributes that are disabled by default because they
are experimental (subject to change). For javaagent tests, these attributes are enabled through
JVM properties such as:

```
otel.instrumentation.<module>.experimental-span-attributes=true
otel.instrumentation.http.client.emit-experimental-telemetry=true
otel.instrumentation.<module>.experimental-metrics.enabled=true
```

The `testExperimental` Gradle task runs the test suite with this flag enabled so that the
experimental attribute assertions are exercised in CI. Plain-library tests enable the corresponding
option through the instrumentation builder, as shown below.

## Detecting and Fixing Missing `testExperimental` Tasks

If any experimental flag appears in `withType<Test>().configureEach` or the default `test {}`
block but there is no dedicated `testExperimental` task, fix it:

1. Create a `testExperimental` task (see Gradle Task Setup below).
2. Move the experimental flag out of the shared/default task config into `testExperimental`.
3. Read the selected mode through a flag constant or shared fixture method and gate experimental
   attribute assertions on it (see Java Test Patterns below).
4. Wire the new task into `check`.

## Gradle Task Setup

The `testExperimental` task follows the standard custom test task pattern — see
[gradle-conventions.md](gradle-conventions.md) for `testClassesDirs`, `classpath`,
`collectMetadata`, `metadataConfig`, and `check` wiring requirements.

When the module creates an experimental variant for every `JvmTestSuite` and source suites have
task-specific JVM arguments or system properties, follow the
[source suite JVM settings](gradle-conventions.md#preserving-source-suite-jvm-settings)
pattern. Inherit the source values, then add the experimental flag. Do not reconstruct source
settings with checks against `suite.name`.

For a javaagent test, the domain-specific parts are the `jvmArgs` and `metadataConfig` values:

```kotlin
val testExperimental = register<Test>("testExperimental") {
  testClassesDirs = sourceSets.test.get().output.classesDirs
  classpath = sourceSets.test.get().runtimeClasspath
  jvmArgs("-Dotel.instrumentation.<module>.experimental-span-attributes=true")
  systemProperty("metadataConfig", "otel.instrumentation.<module>.experimental-span-attributes=true")
}
```

### Programmatically configured library tests

Use an explicitly test-only property to select a builder option in a plain-library fixture.
Do not reuse a javaagent property name for a switch that only the test code reads. Suite-owned
executions bind the variant to its source suite and wire it into `check`. Import
`io.opentelemetry.instrumentation.gradle.executions` in the build script:

```kotlin
testing {
  suites {
    named<JvmTestSuite>("test") {
      executions {
        register("ExperimentalAttributes") {
          systemProperty("test.captureExperimentalSpanAttributes", "true")
          systemProperty(
            "metadataConfig",
            "AwsSdkTelemetryBuilder.setCaptureExperimentalSpanAttributes(true)",
          )
        }
      }
    }
  }
}
```

The fixture applies the selected option through the library API:

```java
AwsSdkTelemetry.builder(testing().getOpenTelemetry())
    .setCaptureExperimentalSpanAttributes(captureExperimentalSpanAttributes())
    .build();
```

Keep baseline tests on builder defaults and apply optional settings in named variants. When sharing
tests with the javaagent, use the
[shared fixture method](testing-general-patterns.md#shared-agent-and-library-fixtures)
to select the configuration source for both builder setup and assertions.
Libraries that actually read production properties, including library-autoconfigure integrations,
still use those properties. Test-only flags are not production configuration and do not belong in
metadata labels. Follow the
[canonical telemetry collection guidance](../../../instrumentation-docs/readme.md#telemetry-collection)
for default and combined profiles, including synthetic header setup.

## Java Test Patterns

For the cross-cutting shape — inline ternary with `null` for "absent", when to use top-level
`if` blocks, and `assumeTrue(...)` guidance — see
[testing-general-patterns.md](testing-general-patterns.md#flag-gated--mode-dependent-assertions).
The experimental-specific patterns below build on that shape.

### Hoist the flag into a per-class `EXPERIMENTAL_ATTRIBUTES` constant

For tests that all use the same production property, the name is module-specific, so there is no
global shared accessor. Read it once into a `private static final boolean` near the top of the class
and reference the constant everywhere in the file:

```java
private static final boolean EXPERIMENTAL_ATTRIBUTES =
    Boolean.getBoolean("otel.instrumentation.<module>.experimental-span-attributes");
```

When multiple experimental flags coexist in one class, use a more specific name per flag.
When the configuration source depends on the concrete fixture, use the instance method described
above instead of caching the resolved mode in a shared static field.

### Single-arg `experimental(value)` helper

Experimental attributes are by definition absent when the flag is off, so the off-branch is
always `null`. When several assertions in the same class gate attributes on
`EXPERIMENTAL_ATTRIBUTES`, extract a tiny helper:

```java
@Nullable
private static <T> T experimental(T value) {
  return EXPERIMENTAL_ATTRIBUTES ? value : null;
}

equalTo(SOME_KEY, experimental("value"))
```

For multiple test classes sharing the same flag, move the helper into a shared
`ExperimentalTestHelper` class and static-import it.
