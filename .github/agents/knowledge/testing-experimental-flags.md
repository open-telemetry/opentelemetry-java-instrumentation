# [Testing] Experimental Feature Flag Tests

## Quick Reference

- Use when: any `experimental` flag (e.g., `experimental-span-attributes`,
  `emit-experimental-telemetry`, `experimental-metrics.enabled`) appears in JVM args or
  system properties of a test task
- Review focus: task wiring/metadata config and assertion patterns for flag-on vs flag-off behavior

## What `testExperimental` Is For

Some instrumentation modules support extra attributes that are disabled by default because they
are experimental (subject to change). These attributes are gated behind a JVM property such as:

```
otel.instrumentation.<module>.experimental-span-attributes=true
otel.instrumentation.http.client.emit-experimental-telemetry=true
otel.instrumentation.<module>.experimental-metrics.enabled=true
```

The `testExperimental` Gradle task runs the test suite with this flag enabled so that the
experimental attribute assertions are exercised in CI.

## Detecting and Fixing Missing `testExperimental` Tasks

If any experimental flag appears in `withType<Test>().configureEach` or the default `test {}`
block but there is no dedicated `testExperimental` task, fix it:

1. Create a `testExperimental` task (see Gradle Task Setup below).
2. Move the experimental flag out of the shared/default task config into `testExperimental`.
3. Add a `private static final boolean EXPERIMENTAL_ATTRIBUTES` field to test classes.
4. Wrap experimental attribute assertions with the `experimental()` helper.
5. Wire the new task into `check`.

## Gradle Task Setup

The `testExperimental` task follows the standard custom test task pattern — see
[gradle-conventions.md](gradle-conventions.md) for `testClassesDirs`, `classpath`,
`collectMetadata`, `metadataConfig`, and `check` wiring requirements.

The domain-specific parts are the `jvmArgs` and `metadataConfig` values:

```kotlin
val testExperimental by registering(Test::class) {
  testClassesDirs = sourceSets.test.get().output.classesDirs
  classpath = sourceSets.test.get().runtimeClasspath
  jvmArgs("-Dotel.instrumentation.<module>.experimental-span-attributes=true")
  systemProperty("metadataConfig", "otel.instrumentation.<module>.experimental-span-attributes=true")
}
```

## Java Test Patterns

Read the flag once into a `private static final boolean` at class level:

```java
private static final boolean EXPERIMENTAL_ATTRIBUTES =
    Boolean.getBoolean("otel.instrumentation.<module>.experimental-span-attributes");
```

Use inline ternary in assertions — `null` means attribute expected absent:

```java
equalTo(ExperimentalAttributes.SOME_ATTR, EXPERIMENTAL_ATTRIBUTES ? "value" : null)
```

When many assertions share the flag, extract an `experimental()` helper:

```java
@Nullable
private static <T> T experimental(T value) {
  return EXPERIMENTAL_ATTRIBUTES ? value : null;
}
```

For multiple test classes sharing the same flag, move the helper into a shared
`ExperimentalTestHelper` class and static-import it.

Use `assumeTrue(EXPERIMENTAL_ATTRIBUTES)` only when an entire test is meaningful in
experimental mode only — prefer the ternary/helper pattern so both modes are exercised.
