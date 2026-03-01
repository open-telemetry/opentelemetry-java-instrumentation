---
applyTo: "**/src/test/**/*.{java,kt}"
---

# General Testing Instructions

## Rules

Follow `docs/contributing/style-guide.md` testing/tooling guidance.

Prefer AssertJ assertions over JUnit assertions.

Test classes and test methods should generally be package-protected (no explicit `public`).

For span attribute assertions, prefer `hasAttributesSatisfyingExactly(...)` with
`OpenTelemetryAssertions.equalTo(...)` / `satisfies(...)`.

## See Also

For detailed examples and review checks, see:

- `.github/agents/knowledge/testing-general.md`
- `.github/agents/knowledge/testing-experimental-flags.md`
- `.github/agents/knowledge/testing-semconv-dual.md`
