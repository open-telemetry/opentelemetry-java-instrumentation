---
applyTo: "**/*.java"
---

# Java Test Rules (first-pass review)

This file is loaded for all Java changes, but the rules below apply only when
reviewing test code (e.g. `src/test/**`, `src/*Test/**`, and `testing/`
modules). Skip them on production sources.

## [Testing] General Patterns

- Use AssertJ (`assertThat(...)`) for assertions in new test code. Do not
  use JUnit `Assert.*` or Hamcrest `assertThat`.
- Do not add AssertJ `.as(...)` descriptions or `.withFailMessage(...)` in
  tests. Direct assertions whose failure output already shows the unexpected
  values are preferred.
- Test methods do not need `throws Exception` clauses unless actually required.
- Prefer the nearest common parent in `catch` (including `Exception` /
  `Throwable`) over multi-catch.
- Use `e` / `f` / `t` / `ignored` for catch variables (per the catch-variable
  naming rule in `.github/copilot-instructions.md`).

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
  overload exists.

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
- Do not hide mode-dependent expectations in a helper that builds or augments a
  `List<AttributeAssertion>`, such as `databaseAttributes()`, and passes it to
  an assertion helper. Keep each individual attribute expectation at the
  assertion site; retain helpers only for genuinely nontrivial derivation:

  ```java
  // Bad: the helper conditionally builds a list and hides the expected shape.
  private static List<AttributeAssertion> databaseAttributes() {
    List<AttributeAssertion> attributes = new ArrayList<>();
    if (emitStableDatabaseSemconv()) {
      attributes.add(equalTo(DB_SYSTEM_NAME, ELASTICSEARCH));
    }
    return attributes;
  }
  assertNodeListTarget(span, databaseAttributes());

  // Good: each mode-dependent expectation remains visible at the assertion.
  assertNodeListTarget(
      span,
      equalTo(DB_SYSTEM, emitStableDatabaseSemconv() ? null : ELASTICSEARCH),
      equalTo(DB_SYSTEM_NAME, emitStableDatabaseSemconv() ? ELASTICSEARCH : null));
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

Name the outer parameter `val` in Java (or `value` in Scala, where `val` is
reserved). Use `v` only for a nested inner-lambda parameter.

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
