# [Testing] Experimental Feature Flag Tests

## Quick Reference

- Use when: reviewing `testExperimental` tasks or experimental span-attribute assertions
- Review focus: task wiring/metadata config and assertion patterns for flag-on vs flag-off behavior

## What `testExperimental` Is For

Some instrumentation modules support extra attributes that are disabled by default because they
are experimental (subject to change) or carry a performance cost. These attributes are gated
behind a JVM property such as:

```
otel.instrumentation.<module>.experimental-span-attributes=true
```

The `testExperimental` Gradle task runs the test suite with this flag enabled so that the
experimental attribute assertions are exercised in CI.

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
