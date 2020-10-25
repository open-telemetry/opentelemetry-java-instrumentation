# Logger MDC auto-instrumentation

The Mapped Diagnostic Context (MDC) is

> an instrument for distinguishing interleaved log output from different sources.
> &mdash; <cite> [log4j MDC documentation](http://logging.apache.org/log4j/1.2/apidocs/org/apache/log4j/MDC.html) </cite>

It contains thread-local contextual information which is later copied to each logging event captured
by a logging library.

The OTel Java agent injects several pieces of information about the current span into each logging
event's MDC copy:

- `traceId` - the current trace id
  (same as `Span.current().getSpanContext().getTraceIdAsHexString()`);
- `spanId` - the current span id
  (same as `Span.current().getSpanContext().getSpanIdAsHexString()`);
- `sampled` - a boolean flag marking whether the current span is sampled or not
  (same as `Span.current().getSpanContext().isSampled()`).

Those three pieces of information can be included in log statements produced by the logging library
by specifying them in the pattern/format. Example for Spring Boot configuration (which uses logback):

```properties
logging.pattern.console = %d{yyyy-MM-dd HH:mm:ss} - %logger{36} - %msg t:%X{traceId} s:%X{spanId} %n
```

This way any services or tools that parse the application logs can correlate traces/spans with log statements.

## Supported logging libraries

| Library | Version |
|---------|---------|
| Log4j 1 | 1.2+    |
| Log4j 2 | 2.7+    |
| Logback | 1.0+    |
