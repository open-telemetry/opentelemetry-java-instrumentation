---
applyTo: "instrumentation/**/library/**/*.java,instrumentation/**/library/**/*.kt,instrumentation/**/library-autoconfigure/**/*.java,instrumentation/**/library-autoconfigure/**/*.kt"
---

# Library instrumentation

- New public `*Telemetry` entry points provide `create(OpenTelemetry)` or
  `builder(OpenTelemetry)`. An entry point without customization may omit a builder.
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
  types from other overloads, even those the caller does not use.
- Library artifacts run without javaagent transformation. Choose per-object state storage
  supported by the library API and lifecycle rather than assuming field injection.
- When a factory or builder returns an instrumented implementation of an existing underlying-library
  type already used by application code, prefer that library's application-facing interface or base
  class over the OpenTelemetry implementation class when it is the complete intended caller-facing
  contract. This applies to library objects, not OpenTelemetry-defined types or SPIs; concrete types
  needed for supported setup or framework integration are not automatic violations.

For an entry point with customization, the convenience factory delegates to its builder:

```java
public final class MyLibraryTelemetry {
  public static MyLibraryTelemetry create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  public static MyLibraryTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new MyLibraryTelemetryBuilder(openTelemetry);
  }
}
```

An entry point with no customization can expose only `create(OpenTelemetry)`; it does not need
a builder solely for symmetry with other modules.
