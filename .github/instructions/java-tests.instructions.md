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

## [Testing] AssertJ Idiomatic Simplifications

Prefer built-in AssertJ collection/string/map assertions over manual extraction:

| Anti-pattern | Idiomatic |
| --- | --- |
| `assertThat(list.size()).isEqualTo(N)` | `assertThat(list).hasSize(N)` |
| `assertThat(list.isEmpty()).isTrue()` / `.hasSize(0)` | `assertThat(list).isEmpty()` |
| `assertThat(list.contains(x)).isTrue()` | `assertThat(list).contains(x)` |
| per-index `get(i)` checks of every element | `assertThat(list).containsExactly(a, b, ...)` |

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
