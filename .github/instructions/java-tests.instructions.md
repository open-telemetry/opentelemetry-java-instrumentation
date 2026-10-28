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

- Prefer integration tests for instrumentation behavior. Exercise the library through its normal
  API, use a real service when relevant, and assert emitted telemetry. For javaagent changes, use
  the installed agent against the required library versions to exercise class transformation and
  library compatibility.
- Use focused unit tests for nontrivial standalone logic and shared infrastructure when they add
  coverage beyond integration tests. Choose cases for distinct behavior and important edge cases,
  not to enumerate every implementation branch or combination of mocked responses.
- Tests that exercise javaagent helper classes directly, without requiring installed-agent behavior,
  belong in a `JvmTestSuite` named `unitTests` under `src/unitTests/java`. Prefer direct same-package
  access over `AgentClassLoaderAccess` or reflection used solely to bypass integration-test
  classloader isolation. Reflection or classloader access remains appropriate when that behavior
  is itself under test.
- Test supported behavior. Do not add or request tests that only prove
  removed settings are ignored, or preview variants that repeat the same behavior.
  When configuration wiring changes, test the real initialization path and emitted
  telemetry, not just helpers.
- For ordinary settings available in both forms, prefer default-behavior and
  module-specific flat-property tests. Do not add or request duplicate behavior tests for
  equivalent flat properties and declarative YAML that use shared configuration logic. Add declarative-mode
  coverage only for different behavior, new or changed mode-specific wiring or
  migration paths, structured configuration, or settings unavailable through flat properties.
  Keep mode-specific wiring tests focused rather than repeating the full behavior matrix.
  Test shared flat-to-declarative conversion centrally. Single-source declarative unit tests are not duplicates.
- Test shared-helper defaults, fallback, and precedence centrally, not in every caller.
  Keep caller-specific logic covered and add missing central coverage before removing a module test.
- Do not add or request historical-absence assertions merely because telemetry
  or functionality was removed or made opt-in. An absence assertion must verify
  a supported contract that would merit testing even if the previous behavior
  had never existed; do not mechanically replace old presence assertions with
  absence assertions in unrelated integration or smoke tests. Keep focused
  coverage for supported settings, changed defaults, explicit opt-in/opt-out,
  and default enablement, as well as exact assertions of supported telemetry.
- For a module whose default enablement changes, the same representative
  operation must emit instrumentation telemetry in an enabled JVM and no
  instrumentation telemetry in a disabled JVM. A `DefaultEnablementTest`
  can run in both modes through `testDisabled`. In the negative mode,
  wait for operation completion and assert exactly a manually created
  parent span; an immediate `spans().isEmpty()` can pass before export.
- Instrumentation selectors such as `otel.instrumentation.<name>.enabled`
  are escape hatches for buggy instrumentation, not general telemetry-tuning
  settings. Do not add or request per-instrumentation selector tests for new
  modules, aliases, renames, or consolidations. This includes module name-list
  assertions, enable/disable matrices, precedence, legacy-name handling,
  flat/YAML parity, warning checks, and dedicated JVM variants or test projects.
  Test shared enablement or alias-helper changes centrally. Keep coverage for
  actual default-enablement changes as described above, feature settings, and
  instrumentation behavior; tests may use selectors to isolate that behavior.
- For starter tests, check `smoke-tests-otel-starter/` for real Spring
  starter coverage; `smoke-tests/images/spring-boot` tests the javaagent
  instead. Declarative mode uses separate `testDeclarativeConfig` source
  sets, not a flag toggled inside the normal tests.
- When changed tests exercise behavior behind an experimental feature or
  telemetry flag, including experimental metrics, check assertions in both
  default-off and flag-on JVMs. Run the flag-on assertions through a separate
  `testExperimental` task or an existing equivalent variant; either must be
  wired into `check` or CI. Do not request one for tests unrelated to the flag.

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
- Prefer binding test servers to port `0` when they expose the actual bound port.
  Wait for binding to complete before reading the port and connecting clients;
  keep the server bound rather than probing and releasing a port.
  When ports must be chosen before the server starts, use `PortUtils` to coordinate
  allocations across test processes. Use `findOpenPorts(count)` for a consecutive
  range instead of assuming adjacent ports are available.
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

## [Testing] Discarding setup and cleanup telemetry

- Inspect setup and cleanup operations before each `testing.clearData()` and
  before an `InstrumentationExtension` lifecycle clear, not just the final
  telemetry assertions. When an operation can complete or export spans
  asynchronously, wait for its traces before clearing. A synchronous client
  call returning does not by itself establish that all telemetry was captured.
- For telemetry that is only being discarded, use
  `testing.waitForTraces(expectedTraceCount)`. Do not introduce span-name or
  attribute assertions, `waitAndAssertTraces(...)`, or assertion helpers solely
  to wait for setup telemetry. Keep assertions when that telemetry's contents
  are themselves part of the behavior under test.
- Use the total trace count expected at that point and wait for all setup
  traces, not just the first one. Add a count-based wait only when the count
  is deterministic. If retries, concurrent/background work, or external-system
  behavior can vary it, use an appropriate completion signal rather than
  guessing a count or adding a sleep.
- `InstrumentationExtension` already clears captured telemetry before each
  test. Keep any necessary wait at the end of setup or cleanup even when
  removing a redundant explicit clear. Do not add or keep an explicit clear
  solely for per-test isolation; keep explicit clears for required mid-test resets.
- Do not wait for spans that intentionally remain open until the operation
  under test completes, such as an unfinished pipeline or transaction.

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
- In test assertions, use stable or incubating semconv `AttributeKey` constants
  when both the name and type match. Tests can import either artifact directly.
  For non-semconv keys used only in assertions, keep factory calls such as
  `stringKey("name")` and `longKey("name")` inline in `equalTo(...)` rather than
  introducing static key constants.
- Do not introduce redundant `(long)` casts in `equalTo(longKey(...), value)`
  when `value` is already an `int` — the `equalTo(AttributeKey<Long>, int)`
  overload exists. Keep the cast when a nullable conditional expression such
  as `condition ? (long) intValue : null` must produce a boxed `Long`; removing
  it can select the primitive overload and unbox `null`.

## [Testing] Mode-Dependent Expected Values

- Use the shared static `TestLatestDeps.testLatestDeps()` and
  `SemconvStability.emitOld*Semconv()`, `emitStable*Semconv()`, or `emitPreview*Semconv()` accessors,
  preferably via static imports, rather than repeating inline
  `Boolean.getBoolean(...)` calls with the mode property names. A
  module-specific experimental flag may use a per-class constant such as
  `EXPERIMENTAL_ATTRIBUTES`; keep the conventional `experimental(value)`
  helper for attribute values absent when the flag is off.
- Name stable-selection accessors `emitStable*Semconv()` and preview-selection
  accessors `emitPreview*Semconv()`.
- Assert keys and values directly when expectations do not depend on a mode:

  ```java
  equalTo(DB_SYSTEM_NAME, ELASTICSEARCH);
  equalTo(DB_OPERATION_NAME, "info");
  equalTo(ERROR_TYPE, "42601");
  ```

- RPC expectations always use the current conventions directly, without mode checks.
- Keep short conditional expected values directly in the assertion when the
  expected values differ by mode or an attribute exists in only one mode:

  ```java
  equalTo(PEER_SERVICE, emitOldServicePeerSemconv() ? "backend" : null);
  equalTo(SERVICE_PEER_NAME, emitPreviewServicePeerSemconv() ? "backend" : null);
  ```

- Nested ternaries are allowed directly in attribute-assertion expected values. Other test code follows
  the normal conditional-expression rule.
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
  private static List<AttributeAssertion> servicePeerAttributes() {
    List<AttributeAssertion> attributes = new ArrayList<>();
    if (emitOldServicePeerSemconv()) {
      attributes.add(equalTo(PEER_SERVICE, "backend"));
    }
    if (emitPreviewServicePeerSemconv()) {
      attributes.add(equalTo(SERVICE_PEER_NAME, "backend"));
    }
    return attributes;
  }
  span.hasAttributesSatisfyingExactly(servicePeerAttributes());

  // Good: pass each assertion directly and keep its mode check visible.
  span.hasAttributesSatisfyingExactly(
      equalTo(PEER_SERVICE, emitOldServicePeerSemconv() ? "backend" : null),
      equalTo(SERVICE_PEER_NAME, emitPreviewServicePeerSemconv() ? "backend" : null));
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
