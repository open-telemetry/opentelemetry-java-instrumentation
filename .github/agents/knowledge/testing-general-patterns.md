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
