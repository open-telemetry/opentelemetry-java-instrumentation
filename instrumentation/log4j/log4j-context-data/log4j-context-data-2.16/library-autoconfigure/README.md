# Log4j2 Autoconfigure Integration

This module provides a Log4j2 `ContextDataProvider` that injects trace context from active spans
into log context.

To use it, add the module to your application's runtime classpath.

Replace `OPENTELEMETRY_VERSION` with the latest
stable [release](https://search.maven.org/search?q=g:io.opentelemetry.instrumentation).

**Maven**

```xml

<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-log4j-2.16-autoconfigure</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
    <scope>runtime</scope>
  </dependency>
</dependencies>
```

**Gradle**

```kotlin
dependencies {
  runtimeOnly("io.opentelemetry.instrumentation:opentelemetry-log4j-2.16-autoconfigure:OPENTELEMETRY_VERSION")
}
```

`OpenTelemetryContextDataProvider` implements the Log4j2 `ContextDataProvider` SPI, and injects the
trace ID and span ID from an active span into
Log4j's [context data](https://logging.apache.org/log4j/2.x/manual/thread-context.html).

Log4j will automatically pick up the integration when you include this module. The following keys
will be added to the context when a log statement is made when a span is active:

- `trace_id`
- `span_id`
- `trace_flags`

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
