# MDC Instrumentation for Logback version 1.0 and higher

This module integrates instrumentation with Logback by injecting the trace ID and span ID from a
mounted span using a custom Logback appender.

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest
release](https://search.maven.org/search?q=g:io.opentelemetry.instrumentation%20AND%20a:opentelemetry-logback-mdc-1.0).

For Maven, add to your `pom.xml` dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-logback-mdc-1.0</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```kotlin
dependencies {
  runtimeOnly("io.opentelemetry.instrumentation:opentelemetry-logback-mdc-1.0:OPENTELEMETRY_VERSION")
}
```

### Usage

The following demonstrates how you might configure the appender in your `logback.xml` configuration:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
  <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
      <pattern>%d{HH:mm:ss.SSS} trace_id=%X{trace_id} span_id=%X{span_id} trace_flags=%X{trace_flags} %msg%n</pattern>
    </encoder>
  </appender>

  <!-- Just wrap your logging appender, for example ConsoleAppender, with OpenTelemetryAppender -->
  <appender name="OTEL" class="io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppender">
    <appender-ref ref="CONSOLE"/>
  </appender>

  <!-- Use the wrapped "OTEL" appender instead of the original "CONSOLE" one -->
  <root level="INFO">
    <appender-ref ref="OTEL"/>
  </root>

</configuration>
```

> It's important to note you can also use other encoders in the `ConsoleAppender` like [logstash-logback-encoder](https://github.com/logfellow/logstash-logback-encoder).
> This can be helpful when the `Span` is invalid and the `trace_id`, `span_id`, and `trace_flags` are all `null` and are hidden entirely from the logs.

Logging events will automatically have context information from the span context injected. The
following attributes are available for use:

- `trace_id`
- `span_id`
- `trace_flags`

These keys can be customized in your `logback.xml` configuration, for example:

```xml
<appender name="OTEL" class="io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppender">
  <traceIdKey>example_trace_id</traceIdKey>
  <spanIdKey>example_span_id</spanIdKey>
  <traceFlagsKey>example_trace_flags</traceFlagsKey>
</appender>
```

If you set `<addBaggage>true</addBaggage>` in your `logback.xml` configuration,
key/value pairs in [baggage](https://opentelemetry.io/docs/concepts/signals/baggage/) will also be added to the MDC.

- `baggage.<entry_name>`
