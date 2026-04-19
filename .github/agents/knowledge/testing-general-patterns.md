# [Testing] General Test Patterns

## Quick Reference

- Use when: test files (`**/src/test/**`) are in scope
- Review focus: assertion style, test class visibility, resource cleanup patterns, attribute assertion patterns

## Assertion Framework

- JUnit 5, AssertJ assertions (not JUnit `assertEquals`/`assertTrue`).
- Test classes and methods should be package-private (no `public`).
- Do not use AssertJ `.as(...)` descriptions or `.withFailMessage(...)` in tests.
  Prefer direct assertions whose failure output shows the unexpected values.

## Test Resource Cleanup

- In JUnit tests, when an `AutoCloseable` is intended to remain live for most or all of the test
  and only needs cleanup at test end, prefer `AutoCleanupExtension` with `deferCleanup(...)`
  over wrapping most of the test body in try-with-resources.
- For resources created in `@BeforeAll` or other class-scoped setup, prefer
  `AutoCleanupExtension` with `deferAfterAll(...)` over nested `@AfterAll` cleanup
  chains. A single solitary `@AfterAll` is acceptable when `AutoCleanupExtension` is not
  otherwise present or needed in the class.
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
