---
applyTo: "**/*.{java,kt}"
---

# Java Style and Safety Instructions

## Rules

Follow `docs/contributing/style-guide.md`.

Prefer imports over fully qualified class names unless there is a class-name collision.

Public API getters should use `get*` (or `is*` for booleans).

In javaagent code paths, prefer safe no-op behavior when instrumented-library methods are missing.

## See Also

For repository-specific details, see:

- `.github/agents/knowledge/api-deprecation-policy.md`
- `.github/agents/knowledge/config-property-stability.md`
