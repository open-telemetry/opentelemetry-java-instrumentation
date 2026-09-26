---
applyTo: "instrumentation/**/library/**/*.java,instrumentation/**/library/**/*.kt,instrumentation/**/library-autoconfigure/**/*.java,instrumentation/**/library-autoconfigure/**/*.kt"
---

# Library instrumentation

- New public `*Telemetry` entry points provide `create(OpenTelemetry)` or
  `builder(OpenTelemetry)`; do not require a builder when there is no customization.
  Where both exist, `create()` delegates to `builder(...).build()`. Builder setters return
  `this` and carry `@CanIgnoreReturnValue`, the constructor is package-private, and
  `build()` returns the telemetry instance.
- Library artifacts run without javaagent transformation. Do not require `VirtualField`
  for per-object state in library-only code. Import stable semantic-convention constants
  whose name and type match; copy incubating constants locally without adding an
  incubating dependency to the published library.
