# Appender Instrumentation for Log4j2 version 2.17 and higher

This module provides a Log4j2 [appender](https://logging.apache.org/log4j/2.x/manual/appenders.html)
which forwards Log4j2 log events to the
[OpenTelemetry Log SDK](https://github.com/open-telemetry/opentelemetry-java/tree/main/sdk/logs).

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest
release](https://search.maven.org/search?q=g:io.opentelemetry.instrumentation%20AND%20a:opentelemetry-log4j-appender-2.17).

For Maven, add to your `pom.xml` dependencies:

```xml

<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-log4j-appender-2.17</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
    <scope>runtime</scope>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```groovy
runtimeOnly("io.opentelemetry.instrumentation:opentelemetry-log4j-appender-2.17:OPENTELEMETRY_VERSION")
```

### Usage

The following demonstrates how you might configure the appender in your `log4j.xml` configuration:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Configuration status="WARN" packages="io.opentelemetry.instrumentation.log4j.appender.v2_17">
  <Appenders>
    <Console name="Console" target="SYSTEM_OUT">
      <PatternLayout
          pattern="%d{HH:mm:ss.SSS} [%t] %-5level %logger{36} trace_id: %X{trace_id} span_id: %X{span_id} trace_flags: %X{trace_flags} - %msg%n"/>
    </Console>
    <OpenTelemetry name="OpenTelemetryAppender"/>
  </Appenders>
  <Loggers>
    <Root>
      <AppenderRef ref="OpenTelemetryAppender" level="All"/>
      <AppenderRef ref="Console" level="All"/>
    </Root>
  </Loggers>
</Configuration>
```

In this example Log4j2 log events will be sent to both the console appender and
the `OpenTelemetryAppender`.

In order to function, `OpenTelemetryAppender` needs access to an `OpenTelemetry` instance. This can
be programmatically set during application startup as follows:

```java
import io.opentelemetry.instrumentation.log4j.appender.v2_17.OpenTelemetryAppender;
import io.opentelemetry.sdk.OpenTelemetrySdk;

public class Application {

  public static void main(String[] args) {
    OpenTelemetrySdk openTelemetrySdk = // Configure OpenTelemetrySdk

    // Find OpenTelemetryAppender in log4j configuration and install openTelemetrySdk
    OpenTelemetryAppender.install(openTelemetrySdk);

    // ... proceed with application
  }
}
```

Optionally, you can configure `OpenTelemetryAppender` to use the `GlobalOpenTelemetry` instance:
```xml
<OpenTelemetry name="OpenTelemetryAppender" useGlobalOpenTelemetry="true" />
```

Note: Setting `useGlobalOpenTelemetry` causes `OpenTelemetryAppender` to
call `GlobalOpenTelemetry.get()`, which initializes `GlobalOpenTelemetry` and prevents future calls
to `GlobalOpenTelemetry.set(...)`. `GlobalOpenTelemetry.get()` will return a noop instance unless
you've opted
into [autoconfigure](https://github.com/open-telemetry/opentelemetry-java/tree/main/sdk-extensions/autoconfigure)
via:

- Add dependency on `io.opentelemetry:opentelemetry-sdk-extension-autoconfigure:OPENTELEMETRY_VERSION`
- Opt into `GlobalOpenTelemetry` autoconfigure by
  setting `-Dotel.java.global-autoconfigure.enabled=true`
