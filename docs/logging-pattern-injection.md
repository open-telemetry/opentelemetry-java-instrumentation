# Automatic text log correlation

The Java agent can add missing correlation fields to text logging patterns. Enable this at JVM startup:

```properties
otel.instrumentation.logging.pattern-injection.enabled=true
```

The equivalent environment variable is `OTEL_INSTRUMENTATION_LOGGING_PATTERN_INJECTION_ENABLED`. The default is `false`.

Supported layouts are Logback classic 1.0+, Log4j 1.2 PatternLayout, and Log4j2 2.7+ PatternLayout. The corresponding MDC or context-data instrumentation must be enabled. JSON layouts and Log4j2 PatternSelector branches are excluded.

For example, `%d{yyyy-MM-dd HH:mm:ss} %-5p %m%n` becomes:

```text
%d{yyyy-MM-dd HH:mm:ss} %-5p trace_id=%X{trace_id} span_id=%X{span_id} %m%n
```

Missing fields are inserted before the message and its preceding literal text, after existing prefix fields such as the timestamp, level, and thread. Patterns without a message converter are unchanged. Date options and presentation wrappers are preserved. Fields are inserted outside wrappers with a truncation width or regex replacement, and outside Log4j2 length-limited and comparison wrappers, to preserve the original message and correlation values. Existing keyed MDC converters are reused; a whole-MDC converter that already renders the fields needs no additions. Repeated layout initialization does not duplicate fields.

Trace and span keys follow `otel.instrumentation.common.logging.trace-id-key` and `otel.instrumentation.common.logging.span-id-key`. Application-provided correlation values take precedence. Outside a valid span, missing trace/span values render empty.

Automatic injection only adds keys made up of ASCII letters, digits, underscores, dots, and hyphens. If a configured key contains any other character, the agent doesn't inject that field and logs a warning once. MDC population and converters already in the pattern are unaffected.
