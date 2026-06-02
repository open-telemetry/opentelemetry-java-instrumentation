# [Testing] General Test Patterns

## Quick Reference

- Use when: test files (`**/src/test/**`) are in scope
- Review focus: assertion style, test class visibility, test method signatures and throws clauses, resource cleanup patterns, attribute assertion patterns

## Assertion Framework

- JUnit 5, AssertJ assertions (not JUnit `assertEquals`/`assertTrue`).
- Test classes and methods should be package-private (no `public`).
- Do not use AssertJ `.as(...)` descriptions or `.withFailMessage(...)` in tests.
  Prefer direct assertions whose failure output shows the unexpected values.

## Parameterized Tests

- When the same test logic is repeated for multiple input/output cases, prefer
  `@ParameterizedTest` over one large test with many unrelated assertions or many small tests that
  duplicate the same setup.
- Prefer `@MethodSource` with a private static `Stream<Arguments>` provider for multi-field cases.
  Keep the provider close to the test that uses it.
- Prefer a human-readable case name as the first parameter so failures identify the scenario
  without reading the whole row.
- Each `Arguments.of(...)` entry should describe one coherent scenario. Prefer one expected outcome
  per row instead of packing several unrelated expectations into a single parameterized case.
- In the test body, keep the setup and assertion flow the same for every row. If different rows need
  materially different control flow, split them into separate tests instead of forcing everything
  into one parameterized method.
- Avoid introducing a dedicated `TestCase` wrapper type for parameterized rows. Prefer a readable
  case name plus plain arguments, and extract a shared expected-value object only when it makes the
  assertions materially clearer.
- If a row gets too wide, first look for ways to simplify the assertion shape (for example, compare
  against one structured expected value) instead of adding another wrapper layer around the row.

Example shape:

```java
@ParameterizedTest(name = "{0}")
@MethodSource("testCases")
void test(String name, Input input, Output expected) {
  assertThat(run(input)).isEqualTo(expected);
}

private static Stream<Arguments> testCases() {
  return Stream.of(
      Arguments.of("valid input", new Input("input"), new Output("expected")));
}
```

## Test Method Throws Clauses

- On methods annotated with `@Test`, keep the `throws` clause to a single exception type.
  Do not declare multiple checked exception types on a test method.
- Be as specific as possible. Prefer the narrowest single checked type that the test body
  actually exposes instead of broad forms such as `throws Exception` or a multi-exception list.
- Apply this guidance only to JUnit test entry points such as `@Test`,
  `@ParameterizedTest`, `@RepeatedTest`, `@TestFactory`, and `@TestTemplate`.
  Do **not** rewrite nearby helpers or utilities, and do **not** introduce new wrapper
  helpers (for example, a `waitForMessage(...)` that catches
  `InterruptedException` / `ExecutionException` / `TimeoutException` and rethrows
  `AssertionError`), solely to narrow a test method's `throws` clause. Do not apply
  this rule to every method in a testing module or abstract test base.
- If the test only blocks on `Future.get(...)` / `CompletableFuture.get(...)`, prefer refactoring to
  `join()` or another non-checked wait path when that keeps the test clear. If no clean
  non-checked wait path exists (for example, when a timeout is required via
  `get(timeout, unit)`), leave the test's `throws` clause as-is — including `throws Exception`
  — rather than inventing a new helper just to narrow it.
- Do **not** replace a direct `CountDownLatch.await(timeout, unit)` assertion with Awaitility
  polling of `getCount()` solely to avoid `InterruptedException` and narrow the test method's
  `throws` clause. Prefer the conventional
  `assertThat(latch.await(timeout, unit)).isTrue()` form for latch-based callback waits, even
  when that means leaving `throws Exception` on the test method.
- Do **not** wrap a checked exception inside a lambda body (for example, catching
  `IOException` and rethrowing `UncheckedIOException`) solely to narrow a test method's
  `throws` clause. That noisy try/catch inside the lambda is worse than leaving
  `throws Exception` (or `throws IOException`) on the `@Test` method. Only introduce such
  wrapping when the lambda already needs its own error handling for behavioral reasons.
- Do **not** choose `CompletableFuture.runAsync(...)` over the simpler
  `executor.submit(runnable).get()` just to avoid the checked exceptions thrown by `Future.get()`.
- Do **not** wrap a call in `assertThatCode(() -> ...).doesNotThrowAnyException()`
  solely to narrow a test method's `throws` clause by swallowing checked exceptions
  into an `AssertionError`. If the call throws, the test already fails via its
  `throws` clause. Prefer calling the method directly and leaving `throws Exception`
  (or the narrowest checked type) on the `@Test` method.

## Test Resource Cleanup

- In JUnit tests, when an `AutoCloseable` is intended to remain live for most or all of the test
  and only needs cleanup at test end, prefer `AutoCleanupExtension` with `deferCleanup(...)`
  over wrapping most of the test body in try-with-resources.
- For class-scoped cleanup, prefer `AutoCleanupExtension.deferAfterAll(...)`
  registered next to the resource's construction in `@BeforeAll`. The same
  applies to per-test cleanup with `deferCleanup(...)` registered in
  `@BeforeEach` or in the test body. This keeps creation and cleanup together
  and avoids a separate `@AfterAll` / `@AfterEach` that re-references and
  null-checks the field. Prefer a plain `@AfterAll` / `@AfterEach` when
  null-checking the field is not needed for correct cleanup. Null-checking is
  needed when a later step in `@BeforeAll` / `@BeforeEach` can throw before all
  closeable fields are assigned — JUnit still runs the teardown in that case,
  and an unguarded close on a null field NPEs and skips cleanup of resources
  initialized earlier (e.g. a still-running test container).
- Reuse an existing `cleanup` extension when one is already in scope.
  Otherwise, add a `@RegisterExtension` field when the deferred-cleanup pattern improves
  clarity or avoids wrapping most of the test body.
- Keep try-with-resources for semantically scoped resources whose lifetime must end before the
  rest of the test continues, such as `Scope` / `Context.makeCurrent()`, Mockito
  `MockedStatic`, and short-lived readers, writers, streams, response bodies, or parsers.
- Keep `AutoCleanupExtension` usage scoped to JUnit-managed test classes; do not introduce it in
  generic helper utilities or other non-JUnit code.
- In `@BeforeAll` or other shared setup code where cleanup is not tied to a single test method,
  use `deferAfterAll(...)` instead of `deferCleanup(...)`.
- If the test intentionally closes the resource mid-test or asserts behavior around explicit
  close, keep the direct close or try-with-resources in the test body.

## Abstract Test Base Classes — Per-Class State Goes On Instance Fields

When an abstract test base is shared by multiple concrete subclasses run in the same JVM
fork, `static` fixture fields become JVM-wide state the subclasses fight over. Annotate
the base with `@TestInstance(Lifecycle.PER_CLASS)` and make per-class fixtures
**instance** fields. JUnit creates one base instance per concrete subclass and reuses it
for every `@Test`, giving the same once-per-class lifecycle as `static` without leaking
across subclasses.

What goes where:

- **Instance** (`protected`, not `protected static`): containers, clients, connections,
  `host` / `port` / URI fields, and any helper method that reads them (e.g.
  `spanName(String)` reading `host`/`port`).
- **Instance** `@RegisterExtension AutoCleanupExtension cleanup`: a shared `static`
  `AutoCleanupExtension` latches `rootContext` on the first subclass's `beforeAll` and
  never resets it, so `deferAfterAll(...)` callbacks registered by later subclasses
  silently leak.
- **`static`** for true JVM-wide constants only: `DB_INDEX`, loggers, `*_HASH_MAP`
  literals, pure helpers (`addExtraAttributes(...)`, `assertCommandEncodeEvents(...)`),
  and `@RegisterExtension InstrumentationExtension testing` — the agent/SDK extension
  must be `static final` because JUnit only invokes `BeforeAllCallback` /
  `AfterAllCallback` for class-level (static) `@RegisterExtension` fields.

This shape is also required when a subclass needs to mutate the container's builder
(e.g. `withCommand("--requirepass password")`): with shared `static` state an earlier
subclass may have already started the container, so `withCommand` on the running
instance is a no-op; with per-instance state each subclass mutates and starts its own
fresh container.

## Span Attribute Assertions

- Use `span.hasAttributesSatisfyingExactly(...)` with `equalTo(...)`/`satisfies(...)` for
  attribute checks. Prefer `hasAttributesSatisfyingExactly` over `hasAttributesSatisfying`
  because it is more precise — the non-exact variant silently ignores unexpected attributes.
  Prefer `hasAttributesSatisfyingExactly` over `hasAttributes(...)` for consistency. For
  zero-attribute assertions, use `hasTotalAttributeCount(0)`.
- `hasTotalAttributeCount(...)` is redundant when paired with
  `hasAttributesSatisfyingExactly(...)` in the same assertion chain, because the exact
  variant already validates the total attribute count. Remove the `hasTotalAttributeCount`
  call.
- These rules apply to **span** attribute assertions only. Metric point assertions are
  different: metric point assertions do not have a `hasTotalAttributeCount(...)` method, so
  the span guidance about preferring `hasTotalAttributeCount(0)` for zero-attribute checks
  does not apply. For metric points, used `point.hasAttributes(Attributes.empty())` — it
  reads more clearly than the no-arg `hasAttributesSatisfyingExactly()` form.
- For non-semconv attribute keys in `equalTo(...)`, use inline `AttributeKey` factory
  methods — `longKey("name")`, `stringKey("name")`, etc. — directly in the assertion.
  Do **not** extract them into class-level `private static final AttributeKey<T>` constants.
  Constants are reserved for semconv keys imported from the semconv library.
- Do **not** introduce redundant `(long)` casts in `equalTo(longKey(...), value)` when `value`
  is already an `int` expression or variable. The assertion API already has an
  `equalTo(AttributeKey<Long>, int)` overload, so `equalTo(longKey("iteration"), iteration)` is
  preferred over `equalTo(longKey("iteration"), (long) iteration)`.

## Metric Assertions

- Prefer `InstrumentationExtension.waitAndAssertMetrics(...)` for metric assertions. It waits
  until the supplied assertion passes, so fixed sleeps such as `Thread.sleep(100)`
  usually unnecessary and make tests slower and more fragile.
- `DbConnectionPoolMetricsAssertions.assertConnectionPoolEmitsMetrics()` already uses
  `waitAndAssertMetrics(...)` for each expected pool metric. Keep the pool state being asserted
  valid until this method returns; for example, if the assertion expects a `used` connection point,
  keep the borrowed connection open until after the assertion.
- After removing a metric-producing source or unregistering an observable callback, call
  `testing().clearData()` and then use Awaitility around the simplest assertion that captures the
  intent. For example, when the test expects no metrics from an instrumentation scope, prefer
  `await().untilAsserted(() -> assertThat(testing().metrics()).filteredOn(...).isEmpty())` over
  expanding the check into one `waitAndAssertMetrics(..., AbstractIterableAssert::isEmpty)` call per
  possible metric name. Do not add an exporter-interval sleep before or after `clearData()` solely
  to wait for metrics; the test runners force-flush metrics when reading them.

## Attribute Assertion `satisfies()` Lambda Parameters

**Attribute-assertion `satisfies()` lambda parameters are `AbstractAssert` instances, not raw
values.** Inside a `satisfies(AttributeKey, Consumer)` lambda the parameter (e.g., `taskId`) is
an `AbstractStringAssert<?>` (for string keys), `AbstractLongAssert<?>` (for long keys), etc.
Fluent assertion calls like `taskId.contains(jobName)` or `taskId.startsWith(prefix)` are
already proper AssertJ assertions - they throw on failure. Do **not** wrap them in
`assertThat(value.contains(x)).isTrue()`, which degrades the failure message.

- For `satisfies(AttributeKey, lambda)` outer parameters, use `val` in Java.
  In Scala, use `value` instead, since `val` is a reserved word.
  Do not use short generic alternatives like `k` or `v` for the outer parameter.
- If an attribute-assertion `satisfies(...)` lambda contains a nested inner lambda and a second
  parameter name is required, keep the outer parameter as `val` in Java or `value` in Scala,
  and use `v` for the nested parameter.
- This naming guidance does **not** apply to non-attribute `satisfies(...)` usages such as
  `span.satisfies(...)`, `point.satisfies(...)`, or `assertThat(result).satisfies(...)`.
  In those cases, prefer a descriptive subject name like `spanData`, `pointData`, `resource`,
  or `result` instead of generic names like `val`.

## AssertJ Idiomatic Simplifications

Prefer built-in AssertJ collection/list assertions over extracting values manually:

| Anti-pattern | Idiomatic form |
| --- | --- |
| `assertThat(list.size()).isEqualTo(N)` | `assertThat(list).hasSize(N)` |
| `assertThat(list.isEmpty()).isTrue()` | `assertThat(list).isEmpty()` |
| `assertThat(list).hasSize(0)` | `assertThat(list).isEmpty()` |
| `assertThat(list.contains(x)).isTrue()` | `assertThat(list).contains(x)` |
| sequential `assertThat(list.get(0)).isEqualTo(a)` / `assertThat(list.get(1)).isEqualTo(b)` checking every element | `assertThat(list).containsExactly(a, b)` |

The `containsExactly` form verifies both size and element values in order, making a
separate `hasSize` check redundant.

Similar patterns apply to maps, arrays, and strings:

| Anti-pattern | Idiomatic form |
| --- | --- |
| `assertThat(str.length()).isEqualTo(N)` | `assertThat(str).hasSize(N)` |
| `assertThat(map.size()).isEqualTo(N)` | `assertThat(map).hasSize(N)` |
| `assertThat(array.length).isEqualTo(N)` | `assertThat(array).hasSize(N)` |

## Span Ordering Assertions

Prefer `hasSpansSatisfyingExactly` (order-sensitive) over `hasSpansSatisfyingExactlyInAnyOrder`
unless the span ordering within a trace is genuinely non-deterministic (e.g., concurrent
producers/consumers, thread-pool fan-out, or channel interleaving). Sequential operations
like `repeat {}` loops, single-child traces, and `flux` sequential emission produce spans
in deterministic order — use `hasSpansSatisfyingExactly` for those.

## Flag-Gated / Mode-Dependent Assertions

Several test modes change which attributes, span names, status codes, or span indexes are
expected:

- Experimental attributes (`-Dotel.instrumentation.<module>.experimental-*=true`) — see
  [testing-experimental-flags.md](testing-experimental-flags.md).
- Semconv stability (`-Dotel.semconv-stability.opt-in=...`) — see
  [testing-semconv-stability.md](testing-semconv-stability.md).
- `testLatestDeps` Gradle property — runs against the newest supported library versions
  instead of the pinned earliest-supported ones.

### Read the flag through a shared static helper, not a per-class field

Each flag has a shared static accessor; static-import it and call it directly. Never call
`Boolean.getBoolean("…")` inline and never duplicate the property-name string at the call
site.

| Flag | Shared accessor | Where it lives |
| --- | --- | --- |
| `-PtestLatestDeps=true` | `testLatestDeps()` | `io.opentelemetry.instrumentation.testing.util.TestLatestDeps` (testing-common) |
| `otel.semconv-stability.opt-in=…` | `emitStableDatabaseSemconv()`, `emitOldDatabaseSemconv()`, `emitStableCodeSemconv()`, etc. | `io.opentelemetry.instrumentation.api.internal.SemconvStability` |
| `otel.instrumentation.<module>.experimental-*` | per-module `EXPERIMENTAL_ATTRIBUTES` constant — see [testing-experimental-flags.md](testing-experimental-flags.md) | within the test class |

### Inline ternary in `equalTo(...)` with `null` for "absent"

Push the ternary as deep as possible — into the `equalTo` value or single attribute key —
rather than duplicating two whole `hasAttributesSatisfyingExactly(...)` blocks under a
`flag ? a : b`. The assertion API treats `null` as "expect attribute absent":

```java
equalTo(DB_USER, emitStableDatabaseSemconv() ? null : USER_DB)
equalTo(ERROR_TYPE, emitStableDatabaseSemconv() ? "42601" : null)
equalTo(SOME_KEY, EXPERIMENTAL_ATTRIBUTES ? "value" : null)
span.hasName(testLatestDeps() ? "GET" : "HTTP GET")
.hasParent(trace.getSpan(testLatestDeps() ? 0 : 1))
```
