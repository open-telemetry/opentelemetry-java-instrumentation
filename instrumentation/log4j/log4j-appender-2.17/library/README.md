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
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```groovy
implementation("io.opentelemetry.instrumentation:opentelemetry-log4j-appender-2.17:OPENTELEMETRY_VERSION")
```

### Usage

The following demonstrates how you might configure the appender in your `log4j2.xml` configuration:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Configuration status="WARN">
  <Appenders>
    <Console name="Console" target="SYSTEM_OUT">
      <PatternLayout
          pattern="%d{HH:mm:ss.SSS} [%t] %-5level %logger{36} trace_id: %X{trace_id} span_id: %X{span_id} trace_flags: %X{trace_flags} - %msg%n"/>
    </Console>
    <OpenTelemetry name="OpenTelemetryAppender"/>
  </Appenders>
  <Loggers>
    <Root level="All">
      <AppenderRef ref="OpenTelemetryAppender"/>
      <AppenderRef ref="Console"/>
    </Root>
  </Loggers>
</Configuration>
```

In this example Log4j2 log events will be sent to both the console appender and
the `OpenTelemetryAppender`.

In order to function, `OpenTelemetryAppender` needs access to an `OpenTelemetry` instance. This must
be set programmatically during application startup as follows:

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

#### Settings for the Log4j Appender

Setting can be configured as XML attributes, for example:

```xml
<Appenders>
  <OpenTelemetry name="OpenTelemetryAppender"
      captureMapMessageAttributes="true"
      captureMarkerAttribute="true"
      captureContextDataAttributes="*"
  />
</Appenders>
```

The available settings are:

| XML Attribute                      | Type    | Default | Description                                                                                                                                                                                                |
|------------------------------------|---------|---------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `captureExperimentalAttributes`    | Boolean | `false` | Enable the capture of experimental log attributes `thread.name` and `thread.id`.                                                                                                                           |
| `captureCodeAttributes`            | Boolean | `false` | Enable the capture of [source code attributes]. Note that capturing source code attributes at logging sites might add a performance overhead.                                                              |
| `captureMapMessageAttributes`      | Boolean | `false` | Enable the capture of `MapMessage` attributes.                                                                                                                                                             |
| `captureMarkerAttribute`           | Boolean | `false` | Enable the capture of Log4j markers as attributes.                                                                                                                                                         |
| `captureContextDataAttributes`     | String  |         | Comma separated list of context data attributes to capture. Use the wildcard character `*` to capture all attributes.                                                                                      |
| `numLogsCapturedBeforeOtelInstall` | Integer | 1000    | Log telemetry is emitted after the initialization of the OpenTelemetry Log4j appender with an OpenTelemetry object. This setting allows you to modify the size of the cache used to replay the first logs. |

[source code attributes]: https://github.com/open-telemetry/semantic-conventions/blob/main/docs/general/attributes.md#source-code-attributes
