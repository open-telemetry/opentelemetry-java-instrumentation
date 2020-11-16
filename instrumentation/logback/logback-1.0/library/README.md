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
    <artifactId>opentelemetry-logback-1.0.0</artifactId>
    <version>0.8.0-SNAPSHOT</version>
    <scope>runtime</scope>
  </dependency>
</dependencies>
```

**Gradle**

```kotlin
dependencies {
  runtimeOnly("io.opentelemetry.instrumentation:opentelemetry-logback-1.0:0.8.0-SNAPSHOT")
}
```

**logback.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
  <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
      <pattern>%d{HH:mm:ss.SSS} %X{traceId} %X{spanId} %msg%n</pattern>
    </encoder>
  </appender>

  <!-- Just wrap your logging appender, for example ConsoleAppender, with OpenTelemetryAppender -->
  <appender name="OTEL" class="io.opentelemetry.instrumentation.logback.v1_0_0.OpenTelemetryAppender">
    <appender-ref ref="CONSOLE" />
  </appender>
  ...
</configuration>
```

Logging events will automatically have context information from the span context injected. The
following attributes are available for use:

- `traceId`
- `spanId`
- `traceFlags`
