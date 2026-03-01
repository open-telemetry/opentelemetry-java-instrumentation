---
applyTo: "**/*.{java,kt,kts}"
---

# Semconv Usage Instructions

## Rules

In `library/src/main`, copy incubating semconv constants into local `private static final` fields with a `// copied from <ClassName>` comment.

In `library/src/main`, stable semconv constants may be imported directly.

In `javaagent/src/main` and tests, semconv constants (stable and incubating) may be used directly.

## See Also

For dual-mode semconv tests and `maybeStable()` boundaries, see:

- `.github/agents/knowledge/testing-semconv-dual.md`
