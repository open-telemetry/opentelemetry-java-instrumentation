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
  Prefer `hasAttributesSatisfyingExactly` over non-empty `hasAttributes(...)` for consistency.
  `hasAttributes(Attributes.empty())` is acceptable.

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
