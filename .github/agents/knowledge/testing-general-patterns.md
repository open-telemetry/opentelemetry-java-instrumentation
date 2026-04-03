# [Testing] General Test Patterns

## Quick Reference

- Use when: test files (`**/src/test/**`) are in scope
- Review focus: assertion style, test class visibility, resource cleanup patterns, attribute assertion patterns

## Assertion Framework

- JUnit 5, AssertJ assertions (not JUnit `assertEquals`/`assertTrue`).
- Test classes and methods should be package-private (no `public`).
- Do not use AssertJ `.as(...)` descriptions or `.withFailMessage(...)` in tests.
  Prefer direct assertions whose failure output shows the unexpected values.

## Parameterized Tests

- Prefer `@ParameterizedTest` when multiple tests in the same file exercise the same behavior
  with small input or expectation changes. This is a good fit when setup, action, and assertion
  shape are identical or nearly identical.
- Do **not** force unrelated scenarios into a parameterized test when the setup diverges
  materially, the assertion logic branches heavily, or separate test names document distinct
  behavior more clearly.
- Prefer the narrowest parameter source that keeps the test readable:
  `@ValueSource` / `@EnumSource` for a single simple input, `@CsvSource` for a few scalar input
  and expectation tuples, and `@MethodSource` for richer inputs such as objects, lambdas,
  callbacks, or larger expectation structures.
- Prefer readable invocation names. When the default argument rendering would be noisy or
  unhelpful, use `Named` values in the method source and reference them from
  `@ParameterizedTest(name = "{0}")` instead of threading a separate display-name `String`
  through the test method signature.
- If the raw argument values are already concise and readable — for example simple strings,
  enums, or small scalar tuples — prefer plain arguments over `Named`, and prefer the default
  parameterized-test invocation rendering unless a custom `name = ...` materially improves
  readability.
- In mixed cases within the same file, apply that rule per source: keep `Named` for opaque
  values such as lambdas or method references, and use plain arguments for readable enums,
  strings, or other self-describing values.
- Keep the parameterized test body focused on the shared behavior. Push case-specific data into
  the method source or a small helper instead of branching inside the test body.
- When a method source is only used by one adjacent test or test cluster, place it immediately
  after that test cluster. When the same source is reused by several separated tests, or when a
  file has several sources that would interrupt the main test flow, keep the sources together at
  the bottom of the file.

## Test Resource Cleanup

- In JUnit tests, when an `AutoCloseable` is intended to remain live for most or all of the test
  and only needs cleanup at test end, prefer `AutoCleanupExtension` with `deferCleanup(...)`
  over wrapping most of the test body in try-with-resources.
- Reuse an existing `cleanup` extension when one is already in scope.
  Otherwise, add a `@RegisterExtension` field when the deferred-cleanup pattern improves
  clarity or avoids wrapping most of the test body.
- Keep try-with-resources for semantically scoped resources whose lifetime must end before the
  rest of the test continues, such as `Scope` / `Context.makeCurrent()`, Mockito
  `MockedStatic`, and short-lived readers, writers, streams, response bodies, or parsers.
- Do not use `AutoCleanupExtension` in non-JUnit helper methods, `@BeforeAll`, or other shared
  setup code where cleanup is not tied to a single test method.
- If the test intentionally closes the resource mid-test or asserts behavior around explicit
  close, keep the direct close or try-with-resources in the test body.

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
- For non-semconv attribute keys in `equalTo(...)`, use inline `AttributeKey` factory
  methods — `longKey("name")`, `stringKey("name")`, etc. — directly in the assertion.
  Do **not** extract them into class-level `private static final AttributeKey<T>` constants.
  Constants are reserved for semconv keys imported from the semconv library.
- Do **not** introduce redundant `(long)` casts in `equalTo(longKey(...), value)` when `value`
  is already an `int` expression or variable. The assertion API already has an
  `equalTo(AttributeKey<Long>, int)` overload, so `equalTo(longKey("iteration"), iteration)` is
  preferred over `equalTo(longKey("iteration"), (long) iteration)`.

## Attribute Assertion `satisfies()` Lambda Parameters

**Attribute-assertion `satisfies()` lambda parameters are `AbstractAssert` instances, not raw
values.** Inside a `satisfies(AttributeKey, Consumer)` lambda the parameter (e.g., `taskId`) is
an `AbstractStringAssert<?>` (for string keys), `AbstractLongAssert<?>` (for long keys), etc.
Fluent assertion calls like `taskId.contains(jobName)` or `taskId.startsWith(prefix)` are
already proper AssertJ assertions — they throw on failure. Do **not** wrap them in
`assertThat(value.contains(x)).isTrue()`, which degrades the failure message.

- For `satisfies(AttributeKey, lambda)` outer parameters, use `val`.
  Do not use short generic alternatives like `k` or `v` for the outer parameter.
- If an attribute-assertion `satisfies(...)` lambda contains a nested inner lambda and a second
  parameter name is required, keep the outer parameter as `val` and use `v` for the nested
  parameter.
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
