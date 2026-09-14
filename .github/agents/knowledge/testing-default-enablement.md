# [Testing] Default instrumentation enablement

## Quick reference

- Use when: adding or reviewing coverage for instrumentation that is disabled by default, or that
  becomes disabled by default under `otel.instrumentation.common.v3-preview`
- Review focus: run one representative operation in enabled and disabled modes, prove that the
  operation emits telemetry when enabled, and prove that it emits no instrumentation telemetry
  when disabled

## Test observable behavior

Test enablement through the installed Java agent and a representative library operation. Do not
call `InstrumentationModule.defaultEnabled()` directly, mock `AgentCommonConfig`, or inspect agent
internals. Those approaches either bypass agent configuration or couple the test to class-loader
and installation details.

The operation must produce an instrumentation span when the module is enabled. A negative-only test
can pass even when its operation never reaches instrumented code.

## Use one test in both modes

Put the coverage in a dedicated `DefaultEnablementTest`. Run the same test method under the normal
test task and a `testDisabled` task. Read the startup property into a class constant and use it only
to select the expected trace shape.

For instrumentation that becomes disabled under v3 preview:

```java
class DefaultEnablementTest {

  private static final boolean V3_PREVIEW =
      Boolean.getBoolean("otel.instrumentation.common.v3-preview");

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void defaultEnablement() {
    testing.runWithSpan("parent", () -> runRepresentativeOperation());

    if (V3_PREVIEW) {
      testing.waitAndAssertTraces(
          trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("parent")));
    } else {
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("parent"),
                  span -> span.hasName("expected instrumentation span")));
    }
  }
}
```

Keep the `if` around the complete `waitAndAssertTraces(...)` calls. Do not put it inside the trace
assertion callback. Separate calls make each mode's complete expected output visible.

Do not use `@EnabledIfSystemProperty` or separate positive and negative test methods. One method
must execute the same operation in both JVM configurations.

## The parent span is a completion signal

Wrap the library operation in a manually created parent span. In disabled mode, assert that the
completed trace contains exactly that span:

```java
testing.waitAndAssertTraces(
    trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("parent")));
```

Wait for an asynchronous operation to finish inside the `runWithSpan(...)` callback. Otherwise, the
parent can be exported first, and the exact assertion can pass before an unexpected instrumentation
span arrives.

Do not replace this with an immediate `testing.spans().isEmpty()` assertion. Export is asynchronous,
so an unexpected instrumentation span could arrive after the assertion. The parent span gives
`waitAndAssertTraces(...)` a completed trace to await, and `hasSpansSatisfyingExactly(...)` rejects
any additional span.

The enabled branch only needs enough detail to prove that the representative operation produced
the expected instrumentation span. Leave full span and attribute coverage in the instrumentation's
ordinary behavior tests.

## Gradle task for a v3-preview default change

Run `DefaultEnablementTest` normally for the positive case. Add a filtered task that starts a new
test JVM with v3 preview enabled for the negative case:

```kotlin
val testDisabled = register<Test>("testDisabled") {
  testClassesDirs = sourceSets.test.get().output.classesDirs
  classpath = sourceSets.test.get().runtimeClasspath
  filter {
    includeTestsMatching("*DefaultEnablementTest")
  }
  jvmArgs("-Dotel.instrumentation.common.v3-preview=true")
}

check {
  dependsOn(testDisabled)
}
```

Set configuration through Gradle JVM arguments because the agent decides which instrumentation to
install during JVM startup. Setting a system property inside the test method is too late.

Add JVM arguments that disable unrelated instrumentation only when the representative operation
would otherwise create extra spans. For example, a Hibernate operation may need JDBC
instrumentation disabled in `testDisabled`.

Do not set `metadataConfig` on `testDisabled` and do not add the task to
`.github/scripts/instrumentations.sh`. The task intentionally produces no telemetry for the target
instrumentation, so it cannot contribute instrumentation metadata.

## Instrumentation that is already disabled by default

Existing tests for an always-disabled module usually enable it explicitly:

```kotlin
jvmArgs("-Dotel.instrumentation.<module>.enabled=true")
```

Keep that enabled run as the positive case. Add a filtered `testDisabled` task that omits the
module-specific enablement property, then branch on that property in `DefaultEnablementTest`:

```java
private static final boolean INSTRUMENTATION_ENABLED =
    Boolean.getBoolean("otel.instrumentation.<module>.enabled");
```

If the enablement argument currently lives in `withType<Test>().configureEach`, move it to the
tasks that should run with the instrumentation enabled. Otherwise `testDisabled` inherits the
argument and does not test the default.
