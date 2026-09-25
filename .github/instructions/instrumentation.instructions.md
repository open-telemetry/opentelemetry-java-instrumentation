---
applyTo: "instrumentation/**/*,instrumentation-api/**/*,instrumentation-api-incubator/**/*"
---

# Instrumentation contracts

Apply a check only if the changed code affects that contract. Inspect unchanged neighboring
files when necessary, but comment on a changed line and only for a supported issue.

## Package naming

- New or renamed library packages use `io.opentelemetry.instrumentation.<library>...`; javaagent
  packages use `io.opentelemetry.javaagent.instrumentation.<library>...`.
- Encode version segments as one identifier such as `v4_0`, not dotted package segments such as
  `v4.0`.
- In versioned common modules, put `common` before the version in both javaagent and library
  packages (`io.opentelemetry.javaagent.instrumentation.<library>.common.v4_0` and
  `io.opentelemetry.instrumentation.<library>.common.v4_0`). A non-versioned javaagent common
  module uses the ordinary javaagent package without a trailing `common` segment; a non-versioned
  library common package may retain one.
