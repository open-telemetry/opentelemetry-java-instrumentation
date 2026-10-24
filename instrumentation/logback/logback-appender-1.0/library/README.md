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

#### Asynchronous logging and trace correlation

Attach the library exporting `OpenTelemetryAppender` directly to your logger, not behind
Logback's `AsyncAppender`. An `AsyncAppender` invokes downstream appenders on its worker thread,
where the application's OpenTelemetry context is unavailable, so exported log records lose
trace/span correlation. Copying trace/span ID strings into MDC does not restore the exported
record's actual span context. This limitation concerns the library exporting appender configuration,
not the Java agent's automatic log capture.

Use the SDK's `BatchLogRecordProcessor` for asynchronous export. Formatting, attribute extraction,
and any synchronous log record processor work still execute on the logging thread (the application
thread when OTel is attached directly). Console or file output can use a separate async branch:

```xml
<configuration>
  <appender name="console" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
      <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
    </encoder>
  </appender>
  <appender name="async-console" class="ch.qos.logback.classic.AsyncAppender">
    <appender-ref ref="console"/>
  </appender>
  <appender name="OpenTelemetry"
            class="io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender"/>
  <root level="INFO">
    <appender-ref ref="OpenTelemetry"/>
    <appender-ref ref="async-console"/>
  </root>
</configuration>
```

For library installation, configure a `BatchLogRecordProcessor` on your `SdkLoggerProvider` and
call `OpenTelemetryAppender.install(openTelemetrySdk)` after Logback configuration, as shown above.
With the OpenTelemetry Spring Boot starter, use the same appender layout in `logback-spring.xml`;
the starter installs the SDK on the exporting appender automatically. The starter's SDK
autoconfiguration uses batch log export by default (`otel.blrp.*` properties tune batching).

The exporting appender reports an advisory Logback status warning once per appender instance if
configuration inspection finds it downstream of a known Logback async appender, including through
nested attachments. Inspection happens when the SDK is installed on the appender, so assemble the
appender graph first. Graphs attached or changed afterward are not guaranteed to be detected unless
SDK installation is performed again. The warning does not reject the configuration or change log
export.

#### Settings for the Logback Appender

Settings can be configured in `logback.xml`, for example:

```xml
<appender name="OpenTelemetry" class="io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender">
  <captureExperimentalAttributes>true</captureExperimentalAttributes>
  <mdcAttributesIncluded>request-*,user-?</mdcAttributesIncluded>
  <mdcAttributesExcluded>*-secret</mdcAttributesExcluded>
  <structuredAttributesExcluded>*-secret</structuredAttributesExcluded>
</appender>
```

The available settings are:

| XML Element                        | Type    | Default | Description                                                                                                                                                                                                                                                                       |
| ---------------------------------- | ------- | ------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `captureExperimentalAttributes`    | Boolean | `false` | Enable the capture of experimental log attributes `thread.name` and `thread.id`.                                                                                                                                                                                                  |
| `captureCodeAttributes`            | Boolean | `false` | Enable the capture of [source code attributes]. Note that capturing source code attributes at logging sites might add a performance overhead.                                                                                                                                     |
| `captureMarkerAttribute`           | Boolean | `false` | Enable the capture of Logback markers as attributes.                                                                                                                                                                                                                              |
| `captureLoggerContext`             | Boolean | `false` | **Deprecated.** Enable the capture of all Logback logger context properties as attributes. It may be removed in the next minor release; use `loggerContextAttributesIncluded` instead.                                                                                            |
| `captureTemplate`                  | Boolean | `false` | Enable the capture of Logback log event message template (if arguments are provided).                                                                                                                                                                                             |
| `captureArguments`                 | Boolean | `false` | Enable the capture of Logback log event arguments.                                                                                                                                                                                                                                |
| `structuredAttributesIncluded`     | String  |         | Comma-separated list of case-sensitive glob patterns for structured attribute keys from SLF4J key value pairs, Logstash markers, and Logstash structured arguments. An absent or empty selector captures all structured attributes.                                               |
| `structuredAttributesExcluded`     | String  |         | Comma-separated list of case-sensitive glob patterns for structured attribute keys not to capture. Excluded patterns take precedence over included patterns. `*` excludes all structured attributes.                                                                              |
| `loggerContextAttributesIncluded`  | String  |         | Comma-separated list of case-sensitive glob patterns for Logback logger context property keys to capture as log attributes.                                                                                                                                                       |
| `loggerContextAttributesExcluded`  | String  |         | Comma-separated list of case-sensitive glob patterns for Logback logger context property keys not to capture as log attributes.                                                                                                                                                   |
| `mdcAttributesIncluded`            | String  |         | Comma-separated list of case-sensitive glob patterns for MDC keys to capture as log attributes.                                                                                                                                                                                   |
| `mdcAttributesExcluded`            | String  |         | Comma-separated list of case-sensitive glob patterns for MDC keys not to capture as log attributes.                                                                                                                                                                               |
| `captureMdcAttributes`             | String  |         | **Deprecated.** Comma-separated list of MDC keys to capture as log attributes. Keys are matched literally, including `*` and `?`, except that the single value `*` captures all MDC attributes. It may be removed in the next minor release; use `mdcAttributesIncluded` instead. |
| `numLogsCapturedBeforeOtelInstall` | Integer | 1000    | Log telemetry is emitted after the initialization of the OpenTelemetry Logback appender with an OpenTelemetry object. This setting allows you to modify the size of the cache used to replay the first logs. thread.id attribute is not captured.                                 |

The same MDC attribute selector can be configured programmatically:

```java
appender.setMdcAttributes(
    IncludeExclude.builder()
        .setIncluded("request-*", "user-?")
        .setExcluded("*-secret")
        .build());
```

MDC keys and selector patterns are matched case-sensitively. `?` matches any single character and
`*` matches any number of characters, including none, so `*` captures all MDC attributes. Excluded
patterns take precedence over included patterns, so a selector with only excluded patterns captures
every MDC attribute that it does not exclude.

MDC attributes are captured only when at least one of these settings is configured. A non-empty
`setMdcAttributes(IncludeExclude)` selector takes precedence over `mdcAttributesIncluded` and
`mdcAttributesExcluded`, which in turn take precedence over the deprecated `captureMdcAttributes`.
No MDC attributes are captured when all of these are absent or empty.

Captured MDC attributes may contain sensitive information. Configure included and excluded patterns
to limit the data exported as log attributes.

Structured attributes from SLF4J 2.x key value pairs, Logstash markers, and Logstash structured
arguments are captured by default. One selector filters all of these sources:

```xml
<appender name="OpenTelemetry" class="io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender">
  <structuredAttributesIncluded>request-*,user-?</structuredAttributesIncluded>
  <structuredAttributesExcluded>*-secret</structuredAttributesExcluded>
</appender>
```

```java
appender.setStructuredAttributes(
    IncludeExclude.builder()
        .setIncluded("request-*", "user-?")
        .setExcluded("*-secret")
        .build());
```

Structured attribute keys and selector patterns are matched case-sensitively. `?` matches any single
character and `*` matches any number of characters, including none. Excluded patterns take precedence
over included patterns, so a selector with only excluded patterns captures every structured
attribute that it does not exclude.

A non-empty `setStructuredAttributes(IncludeExclude)` selector takes precedence over
`structuredAttributesIncluded` and `structuredAttributesExcluded`. An absent or empty selector
captures all structured attributes. Use
`<structuredAttributesExcluded>*</structuredAttributesExcluded>` to exclude all of them.
MDC and logger context capture remain separate and opt-in.

Captured structured attributes may contain sensitive information. Configure included and
excluded patterns to limit the data exported as log attributes.

The logger context properties are selected the same way:

```xml
<appender name="OpenTelemetry" class="io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender">
  <loggerContextAttributesIncluded>app.*</loggerContextAttributesIncluded>
  <loggerContextAttributesExcluded>*-secret</loggerContextAttributesExcluded>
</appender>
```

```java
appender.setLoggerContextAttributes(
    IncludeExclude.builder()
        .setIncluded("app.*")
        .setExcluded("*-secret")
        .build());
```

Logger context property keys and selector patterns are matched case-sensitively. `?` matches any
single character and `*` matches any number of characters, including none, so `*` captures all
logger context properties. Excluded patterns take precedence over included patterns, so a selector
with only excluded patterns captures every logger context property that it does not exclude.

Logger context properties are captured only when at least one of these settings is configured. A
non-empty `setLoggerContextAttributes(IncludeExclude)` selector takes precedence over
`loggerContextAttributesIncluded` and `loggerContextAttributesExcluded`, which in turn take
precedence over the deprecated `captureLoggerContext`. No logger context properties are captured
when all of these are absent or empty.

Captured logger context properties may contain sensitive information. Configure included and
excluded patterns to limit the data exported as log attributes.

The `otel.event.name` key is supported in key-value pairs (SLF4J 2.x fluent API), MDC entries, Logstash markers (e.g., `Markers.append("otel.event.name", ...)`), and Logstash structured arguments (e.g., `StructuredArguments.keyValue("otel.event.name", ...)`). When present, its value is used as the log event name and is not emitted as an attribute.

[source code attributes]: https://github.com/open-telemetry/semantic-conventions/blob/main/docs/general/attributes.md#source-code-attributes
