# Logger MDC auto-instrumentation

The Mapped Diagnostic Context (MDC) is

> an instrument for distinguishing interleaved log output from different sources.
> &mdash; <cite> [log4j MDC documentation](http://logging.apache.org/log4j/1.2/apidocs/org/apache/log4j/MDC.html) </cite>

It contains thread-local contextual information which is later copied to each logging event captured
by a logging library.

The OTel Java agent injects several pieces of information about the current span into each logging
event's MDC copy:

- `trace_id` - the current trace id
  (same as `Span.current().getSpanContext().getTraceId()`);
- `span_id` - the current span id
  (same as `Span.current().getSpanContext().getSpanId()`);
- `trace_flags` - the current trace flags, formatted according to W3C traceflags format
  (same as `Span.current().getSpanContext().getTraceFlags().asHex()`).

Those three pieces of information can be included in log statements produced by the logging library
by specifying them in the pattern/format. This way any services or tools that parse the application
logs can correlate traces/spans with log statements.

> Note: If the current `Span` is invalid, the OpenTelemetry appender will not inject any trace information.

## Supported logging libraries

> Note: There are also log appenders for exporting logs to OpenTelemetry, not to be confused with the MDC appenders.

| Library | Auto-instrumented versions | Standalone Library Instrumentation                                                                                                               |
| ------- | -------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------ |
| Log4j 1 | 1.2+                       |                                                                                                                                                  |
| Log4j 2 | 2.7+                       | [opentelemetry-log4j-context-data-2.17-autoconfigure](../instrumentation/log4j/log4j-context-data/log4j-context-data-2.17/library-autoconfigure) |
| Logback | 1.0+                       | [opentelemetry-logback-mdc-1.0](../instrumentation/logback/logback-mdc-1.0/library)                                                              |

## Frameworks

### Spring Boot

For Spring Boot configuration which uses logback, you can add MDC to log lines by overriding only the `logging.pattern.level`:

```properties
logging.pattern.level = trace_id=%mdc{trace_id} span_id=%mdc{span_id} trace_flags=%mdc{trace_flags} %5p
```
