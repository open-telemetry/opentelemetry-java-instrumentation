# Appender Instrumentation for Logback version 1.0 and higher

This module provides a Logback [appender](https://logback.qos.ch/manual/appenders.html) which
forwards Logback log events to the
[OpenTelemetry Log SDK](https://github.com/open-telemetry/opentelemetry-java/tree/main/sdk/logs).

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest
release](https://central.sonatype.com/artifact/io.opentelemetry.instrumentation/opentelemetry-logback-appender-1.0).

For Maven, add to your `pom.xml` dependencies:

```xml

<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-logback-appender-1.0</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```groovy
implementation("io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0:OPENTELEMETRY_VERSION")
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

In order to function, `OpenTelemetryAppender` needs access to an `OpenTelemetry` instance. This must
be set programmatically during application startup as follows:

```java
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import io.opentelemetry.sdk.OpenTelemetrySdk;

public class Application {

  public static void main(String[] args) {
    OpenTelemetrySdk openTelemetrySdk = // Configure OpenTelemetrySdk

    // Find OpenTelemetryAppender in logback configuration and install openTelemetrySdk
    OpenTelemetryAppender.install(openTelemetrySdk);

    // ... proceed with application
  }
}
```

#### Settings for the Logback Appender

Settings can be configured in `logback.xml`, for example:

```xml
<appender name="OpenTelemetry" class="io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender">
  <captureExperimentalAttributes>true</captureExperimentalAttributes>
  <!-- Include-only MDC selector; excluded patterns are only available programmatically. -->
  <captureMdcAttributes>request-*,user-?</captureMdcAttributes>
</appender>
```

The available settings are:

| XML Element                          | Type    | Default | Description                                                                                                                                                                                                                                       |
| ------------------------------------ | ------- | ------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `captureExperimentalAttributes`      | Boolean | `false` | Enable the capture of experimental log attributes `thread.name` and `thread.id`.                                                                                                                                                                  |
| `captureCodeAttributes`              | Boolean | `false` | Enable the capture of [source code attributes]. Note that capturing source code attributes at logging sites might add a performance overhead.                                                                                                     |
| `captureMarkerAttribute`             | Boolean | `false` | Enable the capture of Logback markers as attributes.                                                                                                                                                                                              |
| `captureKeyValuePairAttributes`      | Boolean | `false` | Enable the capture of Logback key value pairs as attributes.                                                                                                                                                                                      |
| `captureLoggerContext`               | Boolean | `false` | Enable the capture of Logback logger context properties as attributes.                                                                                                                                                                            |
| `captureTemplate`                    | Boolean | `false` | Enable the capture of Logback log event message template (if arguments are provided).                                                                                                                                                             |
| `captureArguments`                   | Boolean | `false` | Enable the capture of Logback log event arguments.                                                                                                                                                                                                |
| `captureLogstashMarkerAttributes`    | Boolean | `false` | Enable the capture of Logstash markers, supported are those added to logs via `Markers.append()`, `Markers.appendEntries()`, `Markers.appendArray()` and `Markers.appendRaw()` methods.                                                           |
| `captureLogstashStructuredArguments` | Boolean | `false` | Enable the capture of Logstash StructuredArguments as attributes (e.g., `StructuredArguments.v()` and `StructuredArguments.keyValue()`).                                                                                                          |
| `captureMdcAttributes`               | String  |         | Include-only MDC selector, and the only MDC setting that can be configured from `logback.xml`. Deprecated in favor of `setMdcAttributes(IncludeExclude)`, which is not reachable from XML, and will be removed in 3.0.                            |
| `numLogsCapturedBeforeOtelInstall`   | Integer | 1000    | Log telemetry is emitted after the initialization of the OpenTelemetry Logback appender with an OpenTelemetry object. This setting allows you to modify the size of the cache used to replay the first logs. thread.id attribute is not captured. |

For programmatic configuration, use an `IncludeExclude` selector:

```java
appender.setMdcAttributes(
    IncludeExclude.builder()
        .setIncluded("request-*", "user-?")
        .setExcluded("*-secret")
        .build());
```

MDC keys and selector patterns are matched case-sensitively. `?` matches any single character and
`*` matches any number of characters, including none. Excluded patterns take precedence over
included patterns. No MDC attributes are captured when the selector is absent or empty; a selector
with only excluded patterns captures every MDC attribute that it does not exclude.

The `otel.event.name` key is supported in key-value pairs (SLF4J 2.x fluent API), MDC entries, Logstash markers (e.g., `Markers.append("otel.event.name", ...)`), and Logstash structured arguments (e.g., `StructuredArguments.keyValue("otel.event.name", ...)`). When present, its value is used as the log event name and is not emitted as an attribute.

[source code attributes]: https://github.com/open-telemetry/semantic-conventions/blob/main/docs/general/attributes.md#source-code-attributes
