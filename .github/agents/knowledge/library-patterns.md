# [Library] TelemetryBuilder and Getter Patterns

## Quick Reference

- Use when: reviewing library instrumentation telemetry/builder/getter/setter patterns
- Review focus: factory entry points, fluent builder contract

## How `{Library}Telemetry` Instances Are Created

Library instrumentations expose a `{Library}Telemetry` class as the user-facing entry point.
Every public `*Telemetry` class provides at least one of `create(OpenTelemetry)` or
`builder(OpenTelemetry)`. The API surface depends on whether customization points exist.

### Common shapes

**Minimal** — `create()` only, no builder (used for simple instrumentations
with no configurable knobs):

```java
public final class MyLibraryTelemetry {
  public static MyLibraryTelemetry create(OpenTelemetry openTelemetry) { ... }
}
```

**With builder** — when users need to configure extractors, headers, etc. (most common —
~80% of modules). `create()` is a convenience shortcut for `builder(ot).build()`:

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

### Conventions

- A simple factory method is named `create(OpenTelemetry)`.
- A builder factory method is named `builder(OpenTelemetry)` and returns a
  `{Library}TelemetryBuilder`.
- Builder setter methods return `this` and are annotated `@CanIgnoreReturnValue`.
- The builder's constructor is package-private — only `Telemetry.builder()` creates it.
- `build()` returns the `{Library}Telemetry` instance.
