# Logback Integration

This module integrates instrumentation with Logback by injecting the trace ID and span ID from a
mounted span using a custom Logback appender.

To use it, add the module to your application's runtime classpath and add the appender to your
`logback.xml`.

**Maven**

```xml

<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-logback-1.0</artifactId>
    <version>0.17.0-alpha</version>
  </dependency>
</dependencies>
```

**Gradle**

```kotlin
dependencies {
  runtimeOnly("io.opentelemetry.instrumentation:opentelemetry-logback-1.0:0.17.0-alpha")
}
```

**logback.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
  <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
      <pattern>%d{HH:mm:ss.SSS} trace_id=%X{trace_id} span_id=%X{span_id} trace_flags=%X{trace_flags} %msg%n</pattern>
    </encoder>
  </appender>

  <!-- Just wrap your logging appender, for example ConsoleAppender, with OpenTelemetryAppender -->
  <appender name="OTEL" class="io.opentelemetry.instrumentation.logback.v1_0.OpenTelemetryAppender">
    <appender-ref ref="CONSOLE"/>
  </appender>
  ...
</configuration>
```

Logging events will automatically have context information from the span context injected. The
following attributes are available for use:

- `trace_id`
- `span_id`
- `trace_flags`
