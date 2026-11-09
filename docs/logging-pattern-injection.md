# Automatic text log correlation

The Java agent can add missing correlation fields to text logging patterns. Enable this at JVM startup:

```properties
otel.instrumentation.logging.pattern-injection.enabled=true
otel.service.name=checkout
```

The equivalent environment variable is `OTEL_INSTRUMENTATION_LOGGING_PATTERN_INJECTION_ENABLED`. The default is `false`.

Supported layouts are Logback classic 1.0+, Log4j 1.2 PatternLayout, and Log4j2 2.7+ PatternLayout. The corresponding MDC or context-data instrumentation must be enabled. JSON layouts and Log4j2 PatternSelector branches are excluded.

For example, `%d{yyyy-MM-dd HH:mm:ss} %-5p %m%n` becomes:

```text
%d{yyyy-MM-dd HH:mm:ss} service.name=%X{service.name} trace_id=%X{trace_id} span_id=%X{span_id} %-5p %m%n
```

Missing fields are inserted after the first complete field. Date options and presentation wrappers are preserved. Existing keyed MDC converters are reused; a whole-MDC converter that already renders the fields needs no additions. Repeated layout initialization does not duplicate fields.

Trace and span keys follow `otel.instrumentation.common.logging.trace-id-key` and `otel.instrumentation.common.logging.span-id-key`. Enabling pattern injection also exposes the configured resource's `service.name` in log events. Application-provided correlation values take precedence. Outside a valid span, the service name remains available and missing trace/span values render empty.
