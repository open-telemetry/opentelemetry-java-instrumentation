---
applyTo: "**/*.java,**/*.kt,**/*.scala"
---

# Java, Kotlin, and Scala tests

Apply behavior-coverage rules when the corresponding production behavior changes.
The remaining sections apply to test code and shared testing modules.

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
  class-loader isolation. Reflection or class-loader access remains appropriate when that behavior
  is itself under test.
- Test supported behavior. Do not add tests that only prove
  removed settings are ignored, or preview variants that repeat the same behavior.
  When configuration wiring changes, test the real initialization path and emitted
  telemetry, not just helpers.
- Do not add historical-absence assertions merely because telemetry
  or functionality was removed or made opt-in. An absence assertion must verify
  a supported contract that would merit testing even if the previous behavior
  had never existed; do not mechanically replace old presence assertions with
  absence assertions in unrelated integration or smoke tests. Keep focused
  coverage for supported settings, changed defaults, explicit opt-in/opt-out,
  and default enablement, as well as exact assertions of supported telemetry.
- Instrumentation selectors such as `otel.instrumentation.<name>.enabled`
  are escape hatches for buggy instrumentation, not general telemetry-tuning
  settings. Do not add per-instrumentation selector tests for new
  modules, aliases, renames, or consolidations. This includes module name-list
  assertions, enable/disable matrices, precedence, legacy-name handling,
  flat/YAML parity, warning checks, and dedicated JVM variants or test projects.
  Test shared enablement or alias-helper changes centrally. Keep coverage for
  actual default-enablement changes, feature settings, and
  instrumentation behavior; tests may use selectors to isolate that behavior.

## General patterns

- In Java, keep JUnit test classes and test methods package-private unless broader visibility is
  required.
- Use AssertJ (`assertThat(...)`) for assertions in new test code. Do not
  use JUnit `Assert.*` or Hamcrest `assertThat`.
- Do not add AssertJ `.as(...)` descriptions or `.withFailMessage(...)` in
  tests. Direct assertions whose failure output already shows the unexpected
  values are preferred.
- Test methods do not need `throws Exception` clauses unless actually required.
  Do not introduce wrappers, exception-catching lambdas, `assertThatCode`, asynchronous
  execution, or polling solely to narrow a test method's `throws` clause.
  A clean non-checked wait such as `join()` is fine when behavior remains appropriate;
  keep checked waits when needed, including `Future.get(timeout, unit)` and
  `assertThat(latch.await(timeout, unit)).isTrue()`.
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
  The trace DSL retries the complete assertion and preserves trace grouping; do not flatten
  `testing.spans()` after a wait to repeat the same exact assertion. Synchronized ad hoc or
  cross-trace filtering remains appropriate when the DSL cannot express the assertion.
- Preserve span order in expected traces when execution order is deterministic.
  Use unordered assertions only when supported concurrency or asynchronous
  execution makes the order nondeterministic.

## Parameterized cases

Prefer `@ParameterizedTest` for repeated input/output scenarios using the same setup and assertion
flow. For multi-field cases, use a nearby private static `Stream<Arguments>` method source and
`Arguments.argumentSet("scenario", ...)` rather than a separate name parameter and
`@ParameterizedTest(name = "{0}")`.

Keep each row coherent. Split cases requiring materially different control flow instead of forcing
them through one parameterized body. Prefer plain arguments over a dedicated `TestCase` wrapper;
use a structured expected value only when it materially clarifies the assertions.

## Resource lifecycle

Keep try-with-resources for lifetimes that end before the rest of the test, such as a `Scope`,
Mockito `MockedStatic`, response body, or short-lived stream. `AutoCleanupExtension` belongs in
JUnit-managed tests, not generic utilities. Reuse an existing extension when one is in scope.
Register deferred cleanup next to construction so a later setup failure cannot leak an earlier
resource. A class-scoped resource uses `deferAfterAll`, not `deferCleanup`.

For subclass-specific fixtures on abstract bases, `@TestInstance(Lifecycle.PER_CLASS)` and instance
fields give each concrete subclass its own state. Clients, containers, addresses, ports, and
helpers reading them must not be shared through static fields. An `AutoCleanupExtension` shared
statically across concrete subclasses can retain the first lifecycle and miss later cleanup.
True constants and pure helpers may remain static; `InstrumentationExtension` remains a
`static final @RegisterExtension` so JUnit invokes its class-level callbacks.

## Metrics

`DbConnectionPoolMetricsAssertions.assertConnectionPoolEmitsMetrics()` already waits for each
expected pool metric. Keep the asserted pool state valid until it returns, including borrowed
connections needed to observe a used-connection point.

After unregistering an observable callback or removing a metric source, clear captured data and
use Awaitility around the simplest relevant absence assertion, such as filtering metrics by
instrumentation scope. Do not enumerate every possible metric name or add exporter-interval
sleeps. The test runners force-flush metrics when reading them.

## Discarding setup and cleanup telemetry

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

For a required mid-test reset after a command producing exactly one completed trace:

```java
jedis.lpush("wrong-type", "value");
testing.waitForTraces(1);
testing.clearData();
```

## AssertJ idioms

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

## Span attributes

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

## Attribute-assertion lambda parameters

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
documents what is being asserted. Lambdas passed to a custom helper such as
`assertExceptionLog(typeAssertion, messageAssertion)` keep descriptive parameter names,
even when the parameter is the same `AbstractStringAssert` type. In those helpers, descriptive
parameter names identify the attribute each lambda asserts; renaming multiple parameters to `val`
loses that context.
