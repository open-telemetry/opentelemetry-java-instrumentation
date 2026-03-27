# [Testing] General Test Patterns

## Quick Reference

- Use when: test files (`**/src/test/**`) are in scope
- Review focus: assertion style, test class visibility, attribute assertion patterns

## Assertion Framework

- JUnit 5, AssertJ assertions (not JUnit `assertEquals`/`assertTrue`).
- Test classes and methods should be package-private (no `public`).

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

## `satisfies()` Lambda Parameters

**`satisfies()` lambda parameters are `AbstractAssert` instances, not raw values.**
Inside a `satisfies(AttributeKey, Consumer)` lambda the parameter (e.g., `taskId`) is an
`AbstractStringAssert<?>` (for string keys), `AbstractLongAssert<?>` (for long keys), etc.
Fluent assertion calls like `taskId.contains(jobName)` or `taskId.startsWith(prefix)` are
already proper AssertJ assertions — they throw on failure. Do **not** wrap them in
`assertThat(value.contains(x)).isTrue()`, which degrades the failure message.

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
