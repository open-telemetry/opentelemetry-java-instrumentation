---
applyTo: "instrumentation/**/library/**/*.java,instrumentation/**/library/**/*.kt,instrumentation/**/library-autoconfigure/**/*.java,instrumentation/**/library-autoconfigure/**/*.kt"
---

# Library instrumentation

- New public `*Telemetry` entry points provide `create(OpenTelemetry)` or
  `builder(OpenTelemetry)`; do not require a builder when there is no customization.
  Where both exist, `create()` delegates to `builder(...).build()`. Builder setters return
  `this` and carry `@CanIgnoreReturnValue`, the constructor is package-private, and
  `build()` returns the telemetry instance.
- Public `*Telemetry` methods that instrument an existing library object and return its
  instrumented form use `instrument(...)`. Prefer overloads over type-name
  or sync/async suffixes when parameter types distinguish the operations and are available
  together on supported consumer compile classpaths.
- Use distinct `instrumentXxx(...)` names when different roles have the same parameter
  signature, or when overloads accept types from optional dependencies that callers may not
  have on their compile classpath. Resolving a call to one overload can require parameter
  types from other overloads, even those the caller does not use. Separate method names
  avoid making callers add unused dependencies just to resolve an overload.
- Library artifacts run without javaagent transformation. Do not require `VirtualField`
  for per-object state in library-only code. Import stable semantic-convention constants
  whose name and type match; copy incubating constants locally without adding an
  incubating dependency to the published library.
- When a factory or builder returns an instrumented implementation of an existing underlying-library
  type already used by application code, prefer that library's application-facing interface or base
  class over the OpenTelemetry implementation class when it is the complete intended caller-facing
  contract. This applies to library objects, not OpenTelemetry-defined types or SPIs; concrete types
  needed for supported setup or framework integration are not automatic violations.
