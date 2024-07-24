# ContextData Instrumentation for Log4j2 version 2.17 and higher

This module provides a Log4j2 `ContextDataProvider` that injects trace context from active spans
into log context.

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest
release](https://search.maven.org/search?q=g:io.opentelemetry.instrumentation%20AND%20a:opentelemetry-log4j-context-data-2.17-autoconfigure).

For Maven, add to your `pom.xml` dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-log4j-context-data-2.17-autoconfigure</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
    <scope>runtime</scope>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```groovy
runtimeOnly("io.opentelemetry.instrumentation:opentelemetry-log4j-context-data-2.17-autoconfigure:OPENTELEMETRY_VERSION")
```

### Usage

`OpenTelemetryContextDataProvider` implements the Log4j2 `ContextDataProvider` SPI, and injects the
trace ID and span ID from an active span into
Log4j's [context data](https://logging.apache.org/log4j/2.x/manual/thread-context.html).

Log4j will automatically pick up the integration when you include this module. The following keys
will be added to the context when a log statement is made when a span is active:

- `trace_id`
- `span_id`
- `trace_flags`

These keys can be customized using the following system properties or environment variables:

| System property                                       | Environment variable                              |
|-------------------------------------------------------|---------------------------------------------------|
| `otel.instrumentation.common.logging.trace-id`        | `OTEL_INSTRUMENTATION_COMMON_LOGGING_TRACE_ID`    |
| `otel.instrumentation.common.logging.span-id`         | `OTEL_INSTRUMENTATION_COMMON_LOGGING_SPAN_ID`     |
| `otel.instrumentation.common.logging.trace-flags`     | `OTEL_INSTRUMENTATION_COMMON_LOGGING_TRACE_FLAGS` |

If the `otel.instrumentation.log4j-context-data.add-baggage` system property (or the
`OTEL_INSTRUMENTATION_LOG4J_CONTEXT_DATA_ADD_BAGGAGE` environment variable) is set to `true`,
key/value pairs in [baggage](https://opentelemetry.io/docs/concepts/signals/baggage/) will also be added to the context data.

- `baggage.<entry_name>`

You can use these keys when defining an appender in your `log4j.xml` configuration, for example:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Configuration>
  <Appenders>
    <Console name="Console" target="SYSTEM_OUT">
      <PatternLayout
          pattern="%d{HH:mm:ss.SSS} [%t] %-5level %logger{36} trace_id=%X{trace_id} span_id=%X{span_id} trace_flags=%X{trace_flags} - %msg%n"/>
    </Console>
  </Appenders>
  <Loggers>
    <Root>
      <AppenderRef ref="Console" level="All"/>
    </Root>
  </Loggers>
</Configuration>
```
