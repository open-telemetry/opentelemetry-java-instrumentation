# [Testing] Default instrumentation enablement

Use this article when testing instrumentation that is disabled by default. The
examples run one operation in enabled and disabled JVMs and verify its observable
telemetry.

This coverage checks which instrumentation runs by default, not the generic
`otel.instrumentation.<name>.enabled` escape hatch. Do not use this pattern to
add selector tests for ordinary modules, aliases, renames, or consolidations.
See [the selector testing boundary](testing-general-patterns.md#instrumentation-enablement-selectors).

## Test observable behavior

Test enablement through the installed Java agent and a representative library operation. Do not
call `InstrumentationModule.defaultEnabled()` directly, mock `AgentCommonConfig`, or inspect agent
internals. Those approaches either bypass agent configuration or couple the test to class-loader
and installation details.

The operation must produce an instrumentation span when the module is enabled. A negative-only test
can pass even when its operation never reaches instrumented code.

## Use one test in both configurations

Put the coverage in a dedicated `DefaultEnablementTest`. Run the same test method under the normal
test task and a `testDisabled` task. Use a startup property only to select the expected trace shape;
do not use separate positive and negative test methods.

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

In both branches, assert only span names. The exact trace assertion still checks the number of
spans, and the names are enough to prove the enablement contract. Leave span kinds, parentage,
status, attributes, and other behavior coverage in the instrumentation's ordinary tests.

## Instrumentation that is disabled by default

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
