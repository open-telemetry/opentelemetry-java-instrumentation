# Appender Instrumentation for Logback version 1.0 and higher

This module provides a Logback [appender](https://logback.qos.ch/manual/appenders.html) which
forwards Logback log events to the
[OpenTelemetry Log SDK](https://github.com/open-telemetry/opentelemetry-java/tree/main/sdk/logs).

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest
release](https://search.maven.org/search?q=g:io.opentelemetry.instrumentation%20AND%20a:opentelemetry-logback-appender-1.0).

For Maven, add to your `pom.xml` dependencies:

```xml

<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-logback-appender-1.0</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
    <scope>runtime</scope>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```groovy
runtimeOnly("io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0:OPENTELEMETRY_VERSION")
```

### Usage

The following demonstrates how you might configure the appender in your `logback.xml` configuration:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>

  <appender name="console" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
      <pattern>
        %d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n
      </pattern>
    </encoder>
  </appender>
  <appender name="OpenTelemetry"
            class="io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender">
  </appender>

  <root level="INFO">
    <appender-ref ref="console"/>
    <appender-ref ref="OpenTelemetry"/>
  </root>

</configuration>
```

In this example Logback log events will be sent to both the console appender and
the `OpenTelemetryAppender`.

In order to function, `OpenTelemetryAppender` needs access to an `OpenTelemetry` instance. This can
be programmatically set during application startup as follows:

```java
import ch.qos.logback.classic.LoggerContext;
import io.opentelemetry.instrumentation.logback.OpenTelemetryAppender;
import io.opentelemetry.sdk.OpenTelemetrySdk;

public class Application {

  public static void main(String[] args) {
    OpenTelemetrySdk openTelemetrySdk = // Configure OpenTelemetrySdk

    // Get LoggerContext, get OpenTelemetryAppender from Root Logger, and call setOpenTelemetrySdk(...)
    LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
    ((OpenTelemetryAppender)
        loggerContext.getLogger(Logger.ROOT_LOGGER_NAME).getAppender("OpenTelemetryAppender"))
        .setOpenTelemetry(openTelemetrySdk);

    // ... proceed with application
  }
}
```

Optionally, you can configure `OpenTelemetryAppender` to use the `GlobalOpenTelemetry` instance:
```xml
<appender name="OpenTelemetry"
  class="io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender">
  <useGlobalOpenTelemetry>true</useGlobalOpenTelemetry>
</appender>
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
