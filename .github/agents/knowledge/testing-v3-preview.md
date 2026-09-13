# [Testing] V3-preview test coverage

## Quick reference

- Use when: a change adds behavior under `otel.instrumentation.common.v3-preview`
- Review focus: test only behavior that differs under v3 preview

The javaagent reads v3-preview configuration during startup, so tests for this mode need a
separate Gradle `Test` task. The separate JVM does not imply that the entire module test suite
needs to run again.

## Choose the smallest useful test scope

Use a filtered test task when v3 preview changes one isolated behavior, such as:

- disabling an instrumentation by default
- changing how one configuration property is interpreted

Run the full test suite under v3 preview only when the mode changes expectations throughout the
suite, such as span names, attributes, or suppression behavior used by most tests.

Do not add a v3-preview task to a module whose observable behavior is unchanged.

## Default-enablement changes

When v3 preview disables an instrumentation by default, use one representative operation to
verify that the instrumentation produces no telemetry. Do not rerun every operation supported by
the instrumentation.

Explicitly setting `otel.instrumentation.<name>.enabled=true` uses shared javaagent configuration
behavior. Do not repeat that behavior in every instrumentation module unless the change introduces
module-specific enablement logic, aliases, or precedence.

When several modules implement the same default-enablement policy, use one representative
integration test per distinct configuration key or behavior. Do not repeat the test for every
supported dependency version.

## Modes reached through multiple settings

Do not repeat an instrumentation test suite when different configuration settings select the same
runtime mode. Test the emitted telemetry under one canonical mode task, then test each setting's
mode selection in the shared configuration component.

For example, database instrumentation that already runs `testStableSemconv` does not also need a
`testV3Preview` task when v3 preview selects the same stable database conventions. The
instrumentation tests cover stable database telemetry. Tests for `SemconvStability` cover whether
v3 preview selects that mode.

## Dependency compatibility suites

Do not derive a preview task from every `JvmTestSuite` merely because those suites exist. Repeat
preview coverage across compatibility suites only when:

- the preview branch selects different instrumentation by library version
- class-loader or type-matcher behavior changes by version
- expected telemetry differs by version

Otherwise, use the default source set and one representative supported version.

## Gradle task shape

For an isolated behavior change, filter the task to the preview-specific test:

```kotlin
tasks {
  val testV3Preview = register<Test>("testV3Preview") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    filter {
      includeTestsMatching("*SessionTest.v3PreviewDisablesHibernateByDefault")
    }

    jvmArgs("-Dotel.instrumentation.common.v3-preview=true")
  }

  check {
    dependsOn(testV3Preview)
  }
}
```

Use the real v3-preview property to keep the preview-specific test out of the normal test run:

```java
@Test
@EnabledIfSystemProperty(
    named = "otel.instrumentation.common.v3-preview",
    matches = "true")
void v3PreviewDisablesHibernateByDefault() {
  // Run one representative operation and verify that Hibernate emits no spans.
}
```

Do not add a second test-only system property when the real property can select the test.

## Metadata collection

Set `metadataConfig` only when metadata collection runs the task and should record that
configuration. When adding it, also add the task to the metadata collection workflow described in
[Gradle conventions](gradle-conventions.md).
