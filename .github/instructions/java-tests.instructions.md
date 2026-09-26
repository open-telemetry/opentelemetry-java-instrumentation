---
applyTo: "**/*.java,**/*.kt,**/*.scala"
---

# Java, Kotlin, and Scala tests

This file is loaded for all Java, Kotlin, and Scala changes. Apply the behavior-coverage
checks when the corresponding production behavior changes. Apply the
remaining sections only to test code and shared testing modules. Comment
only on a changed line for a substantive coverage gap or an explicit
convention not caught by CI.

## Behavior coverage

- For a javaagent change supporting multiple runtime library versions,
  look for tests with the installed agent against the required versions.
  Direct helper tests and `javaagent-unit-tests` do not exercise class
  transformation or the actual library; do not count them as integration
  coverage.
- For a module whose default enablement changes, the same representative
  operation must emit instrumentation telemetry in an enabled JVM and no
  instrumentation telemetry in a disabled JVM. A `DefaultEnablementTest`
  can run in both modes through `testDisabled`. In the negative mode,
  wait for operation completion and assert exactly a manually created
  parent span; an immediate `spans().isEmpty()` can pass before export.
- For starter tests, check `smoke-tests-otel-starter/` for real Spring
  starter coverage; `smoke-tests/images/spring-boot` tests the javaagent
  instead. Declarative mode uses separate `testDeclarativeConfig` source
  sets, not a flag toggled inside the normal tests.

## [Testing] General Patterns

- In Java, keep JUnit test classes and test methods package-private unless broader visibility is
  required.
- Use AssertJ (`assertThat(...)`) for assertions in new test code. Do not
  use JUnit `Assert.*` or Hamcrest `assertThat`.
- Do not add AssertJ `.as(...)` descriptions or `.withFailMessage(...)` in
  tests. Direct assertions whose failure output already shows the unexpected
  values are preferred.
- Test methods do not need `throws Exception` clauses unless actually required.
- Prefer the nearest common parent in `catch` (including `Exception` /
  `Throwable`) over multi-catch.
- Allocate test ports through `PortUtils` so allocations are coordinated across
  the test process. Use `findOpenPorts(count)` when a service needs a consecutive
  range instead of assuming ports adjacent to a separately allocated port are
  available.
- Prefer plain `@AfterEach` or `@AfterAll` teardown when direct cleanup is
  safe. Introduce `AutoCleanupExtension` when deferred cleanup improves
  clarity or protects partially completed setup. Register it with the
  lifecycle that owns the resource: use `deferCleanup` for per-test resources
  and `deferAfterAll` for class-scoped resources. Do not replace deferred
  cleanup with an earlier lifecycle cleanup that can leak on test failure; its
  outermost-container handling intentionally prevents duplicate
  `deferAfterAll` cleanup for nested tests.
- Keep fixture state that depends on a concrete test subclass on that subclass
  or initialize it separately for each subclass. Do not cache subclass-specific
  state in a shared static field on an abstract test base.
- In Scala tests, import `org.assertj.core.api.Assertions.assertThat` for
  ordinary values and call `OpenTelemetryAssertions.assertThat(...)` explicitly
  for telemetry data. Do not statically import both `assertThat` methods.
- Assert complete exported traces with `waitAndAssertTraces(...)` and metrics
  with `waitAndAssertMetrics(...)`; do not use fixed sleeps to wait for telemetry.
  Direct assertions are appropriate only when the test intentionally inspects
  telemetry already synchronized or captured at an intermediate point.
- Preserve span order in expected traces when execution order is deterministic.
  Use unordered assertions only when supported concurrency or asynchronous
  execution makes the order nondeterministic.

## [Testing] Trace Clearing After Asynchronous Operations

- When test setup or cleanup performs an operation that can complete or export
  spans asynchronously, call `testing.waitForTraces(expectedTraceCount)` before
  its captured telemetry is cleared, whether by `testing.clearData()` or an
  `InstrumentationExtension` lifecycle clear. Keep the wait at the end of setup
  or cleanup even when removing a redundant explicit clear. Use the total trace
  count expected at that point so spans exported after the clear cannot leak
  into the next assertion or test.
- Add the wait only when the exact trace count is deterministic. Do not guess
  when retries, concurrent/background work, timing, or external-system behavior
  can vary the count.
- `InstrumentationExtension` already clears captured telemetry before each
  test. Do not add or keep a setup/cleanup `clearData()` solely for per-test
  isolation when that lifecycle clear is sufficient. Keep explicit clears only
  for a required mid-test reset, including to discard telemetry from a
  preceding asynchronous operation after its exports have been drained.

## [Testing] AssertJ Idiomatic Simplifications

Prefer built-in AssertJ collection/string/map assertions over manual extraction:

| Anti-pattern                                          | Idiomatic                                     |
| ----------------------------------------------------- | --------------------------------------------- |
| `assertThat(list.size()).isEqualTo(N)`                | `assertThat(list).hasSize(N)`                 |
| `assertThat(list.isEmpty()).isTrue()` / `.hasSize(0)` | `assertThat(list).isEmpty()`                  |
| `assertThat(list.contains(x)).isTrue()`               | `assertThat(list).contains(x)`                |
| per-index `get(i)` checks of every element            | `assertThat(list).containsExactly(a, b, ...)` |

`containsExactly` already verifies size, so a separate `hasSize` is redundant.
Same shape applies to `String.length()`, `Map.size()`, and `array.length` →
`assertThat(...).hasSize(N)`.

## [Testing] Span Attribute Assertions

- Prefer `hasAttributesSatisfyingExactly(...)` over `hasAttributesSatisfying(...)`
  — the non-exact variant **silently ignores unexpected attributes**. Also
  prefer it over `hasAttributes(...)` for consistency.
- For zero-attribute span assertions, use `hasTotalAttributeCount(0)`.
- `hasTotalAttributeCount(...)` paired with `hasAttributesSatisfyingExactly(...)`
  is redundant — the exact variant already validates the count. Remove the
  count call.
- Metric points are different: there is no `hasTotalAttributeCount(...)` on
  metric points, so use `point.hasAttributes(Attributes.empty())` for empty
  metric-point checks.
- Do not introduce redundant `(long)` casts in `equalTo(longKey(...), value)`
  when `value` is already an `int` — the `equalTo(AttributeKey<Long>, int)`
  overload exists. Keep the cast when a nullable conditional expression such
  as `condition ? (long) intValue : null` must produce a boxed `Long`; removing
  it can select the primitive overload and unbox `null`.

## [Testing] Mode-Dependent Expected Values

- Database instrumentation tests run either the default or stable database
  semconv mode. Do not add `database/dup` test tasks or expand assertions to
  cover both modes at once.
- Use `SemconvStabilityUtil.maybeStable(...)` when old and stable database keys
  carry the same expected value:

  ```java
  equalTo(maybeStable(DB_SYSTEM), ELASTICSEARCH);
  equalTo(maybeStable(DB_OPERATION), "info");
  ```

  Do not replace these with separate null-gated assertions for the old and
  stable keys.
- Keep short conditional expected values directly in the assertion when the
  expected values differ by mode or an attribute exists in only one mode:

  ```java
  span.hasName(emitStableMessagingSemconv() ? "send orders" : "orders publish");
  equalTo(ERROR_TYPE, emitStableDatabaseSemconv() ? "42601" : null);
  ```

- Do not extract the ternary into a helper such as `spanName(...)`,
  `oldOrExperimental(value)`, or `expectedNamespace()` when no established
  semconv utility applies. Seeing both expected values at the assertion is more
  useful than deduplicating a short expression.
- Do not conditionally build a `List<AttributeAssertion>` and then pass that
  list to `hasAttributesSatisfyingExactly(...)`. Pass each assertion directly
  and keep the mode check with its expected value. Retain helpers only for
  genuinely nontrivial derivation:

  ```java
  // Bad: the helper conditionally builds a list and hides the expected shape.
  private static List<AttributeAssertion> databaseAttributes() {
    List<AttributeAssertion> attributes = new ArrayList<>();
    if (emitOldDatabaseSemconv()) {
      attributes.add(equalTo(DB_USER, USER_DB));
    }
    if (emitStableDatabaseSemconv()) {
      attributes.add(equalTo(ERROR_TYPE, "42601"));
    }
    return attributes;
  }
  span.hasAttributesSatisfyingExactly(databaseAttributes());

  // Good: pass each assertion directly and keep its mode check visible.
  span.hasAttributesSatisfyingExactly(
      equalTo(DB_USER, emitOldDatabaseSemconv() ? USER_DB : null),
      equalTo(ERROR_TYPE, emitStableDatabaseSemconv() ? "42601" : null));
  ```

- The conventional `experimental(value)` helper is the one exception: keep it.
  Its name unambiguously means the value is expected only when experimental
  attributes are enabled, and `null` otherwise, so it reads clearer than the
  inlined ternary it would otherwise become.
- A helper may still obtain the mode flag or perform nontrivial derivation from
  test data. It should not choose between short expected values on the
  assertion's behalf.
- Put the ternary around the narrowest value that changes. Do not duplicate a
  whole assertion chain or attribute block for each mode.

## [Testing] `satisfies()` Lambda Parameters

Inside a `satisfies(AttributeKey, lambda)` attribute-assertion the lambda
parameter is an `AbstractAssert` (e.g. `AbstractStringAssert<?>`), not the raw
value. Fluent calls like `taskId.contains(jobName)` are already proper
assertions — do **not** wrap them in `assertThat(value.contains(x)).isTrue()`,
which degrades the failure message.

Name the outer parameter `val` in Java (or `value` in Kotlin and Scala, where
`val` is reserved). Use `v` only for a nested inner-lambda parameter.

This guidance applies only to attribute-assertion `satisfies(...)`; for
`span.satisfies(...)`, `point.satisfies(...)`, etc. use a descriptive name
(`spanData`, `pointData`, `result`).

It also applies only to lambdas written **directly inline** as the
`satisfies(AttributeKey, lambda)` argument, where the attribute key already
documents what is being asserted. Do **not** flag lambdas passed to a custom
helper method (e.g. `assertExceptionLog(typeAssertion, messageAssertion)`),
even though the parameter is the same `AbstractStringAssert` type. There a
descriptive name documents which attribute each lambda asserts, and renaming
multiple parameters to `val` loses that context.
