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
  <mdcAttributesIncluded>request-*,user-?</mdcAttributesIncluded>
  <mdcAttributesExcluded>*-secret</mdcAttributesExcluded>
</appender>
```

The available settings are:

| XML Element                                    | Type    | Default | Description                                                                                                                                                                                                                                                                                                            |
| ---------------------------------------------- | ------- | ------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `captureExperimentalAttributes`                | Boolean | `false` | Enable the capture of experimental log attributes `thread.name` and `thread.id`.                                                                                                                                                                                                                                       |
| `captureCodeAttributes`                        | Boolean | `false` | Enable the capture of [source code attributes]. Note that capturing source code attributes at logging sites might add a performance overhead.                                                                                                                                                                          |
| `captureMarkerAttribute`                       | Boolean | `false` | Enable the capture of Logback markers as attributes.                                                                                                                                                                                                                                                                   |
| `captureKeyValuePairAttributes`                | Boolean | `false` | **Deprecated.** Enable the capture of all Logback key value pairs as attributes. It may be removed in the next minor release; use `keyValuePairAttributesIncluded` instead.                                                                                                                                            |
| `captureLoggerContext`                         | Boolean | `false` | **Deprecated.** Enable the capture of all Logback logger context properties as attributes. It may be removed in the next minor release; use `loggerContextAttributesIncluded` instead.                                                                                                                                 |
| `captureTemplate`                              | Boolean | `false` | Enable the capture of Logback log event message template (if arguments are provided).                                                                                                                                                                                                                                  |
| `captureArguments`                             | Boolean | `false` | Enable the capture of Logback log event arguments.                                                                                                                                                                                                                                                                     |
| `captureLogstashMarkerAttributes`              | Boolean | `false` | **Deprecated.** Enable the capture of all Logstash markers as attributes, supported are those added to logs via `Markers.append()`, `Markers.appendEntries()`, `Markers.appendArray()` and `Markers.appendRaw()` methods. It may be removed in the next minor release; use `logstashMarkerAttributesIncluded` instead. |
| `captureLogstashStructuredArguments`           | Boolean | `false` | **Deprecated.** Enable the capture of all Logstash StructuredArguments as attributes (e.g., `StructuredArguments.v()` and `StructuredArguments.keyValue()`). It may be removed in the next minor release; use `logstashStructuredArgumentAttributesIncluded` instead.                                                  |
| `keyValuePairAttributesIncluded`               | String  |         | Comma-separated list of case-sensitive glob patterns for Logback key value pair keys to capture as log attributes.                                                                                                                                                                                                     |
| `keyValuePairAttributesExcluded`               | String  |         | Comma-separated list of case-sensitive glob patterns for Logback key value pair keys not to capture as log attributes.                                                                                                                                                                                                 |
| `loggerContextAttributesIncluded`              | String  |         | Comma-separated list of case-sensitive glob patterns for Logback logger context property keys to capture as log attributes.                                                                                                                                                                                            |
| `loggerContextAttributesExcluded`              | String  |         | Comma-separated list of case-sensitive glob patterns for Logback logger context property keys not to capture as log attributes.                                                                                                                                                                                        |
| `logstashMarkerAttributesIncluded`             | String  |         | Comma-separated list of case-sensitive glob patterns for Logstash marker keys to capture as log attributes.                                                                                                                                                                                                            |
| `logstashMarkerAttributesExcluded`             | String  |         | Comma-separated list of case-sensitive glob patterns for Logstash marker keys not to capture as log attributes.                                                                                                                                                                                                        |
| `logstashStructuredArgumentAttributesIncluded` | String  |         | Comma-separated list of case-sensitive glob patterns for Logstash structured argument keys to capture as log attributes.                                                                                                                                                                                               |
| `logstashStructuredArgumentAttributesExcluded` | String  |         | Comma-separated list of case-sensitive glob patterns for Logstash structured argument keys not to capture as log attributes.                                                                                                                                                                                           |
| `mdcAttributesIncluded`                        | String  |         | Comma-separated list of case-sensitive glob patterns for MDC keys to capture as log attributes.                                                                                                                                                                                                                        |
| `mdcAttributesExcluded`                        | String  |         | Comma-separated list of case-sensitive glob patterns for MDC keys not to capture as log attributes.                                                                                                                                                                                                                    |
| `captureMdcAttributes`                         | String  |         | **Deprecated.** Comma-separated list of MDC keys to capture as log attributes. Keys are matched literally, including `*` and `?`, except that the single value `*` captures all MDC attributes. It may be removed in the next minor release; use `mdcAttributesIncluded` instead.                                      |
| `numLogsCapturedBeforeOtelInstall`             | Integer | 1000    | Log telemetry is emitted after the initialization of the OpenTelemetry Logback appender with an OpenTelemetry object. This setting allows you to modify the size of the cache used to replay the first logs. thread.id attribute is not captured.                                                                      |

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

The key value pair attributes captured from the SLF4J 2.x fluent API are selected the same way:

```xml
<appender name="OpenTelemetry" class="io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender">
  <keyValuePairAttributesIncluded>request-*,user-?</keyValuePairAttributesIncluded>
  <keyValuePairAttributesExcluded>*-secret</keyValuePairAttributesExcluded>
</appender>
```

```java
appender.setKeyValuePairAttributes(
    IncludeExclude.builder()
        .setIncluded("request-*", "user-?")
        .setExcluded("*-secret")
        .build());
```

Key value pair keys and selector patterns are matched case-sensitively. `?` matches any single
character and `*` matches any number of characters, including none, so `*` captures all key value
pair attributes. Excluded patterns take precedence over included patterns, so a selector with only
excluded patterns captures every key value pair attribute that it does not exclude.

Key value pair attributes are captured only when at least one of these settings is configured. A
non-empty `setKeyValuePairAttributes(IncludeExclude)` selector takes precedence over
`keyValuePairAttributesIncluded` and `keyValuePairAttributesExcluded`, which in turn take precedence
over the deprecated `captureKeyValuePairAttributes`. No key value pair attributes are captured when
all of these are absent or empty.

Captured key value pair attributes may contain sensitive information. Configure included and
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

The Logstash marker attributes are selected the same way:

```xml
<appender name="OpenTelemetry" class="io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender">
  <logstashMarkerAttributesIncluded>request-*</logstashMarkerAttributesIncluded>
  <logstashMarkerAttributesExcluded>*-secret</logstashMarkerAttributesExcluded>
</appender>
```

```java
appender.setLogstashMarkerAttributes(
    IncludeExclude.builder()
        .setIncluded("request-*")
        .setExcluded("*-secret")
        .build());
```

Logstash marker keys and selector patterns are matched case-sensitively. `?` matches any single
character and `*` matches any number of characters, including none, so `*` captures all Logstash
marker attributes. Excluded patterns take precedence over included patterns, so a selector with only
excluded patterns captures every Logstash marker attribute that it does not exclude.

Logstash marker attributes are captured only when at least one of these settings is configured. A
non-empty `setLogstashMarkerAttributes(IncludeExclude)` selector takes precedence over
`logstashMarkerAttributesIncluded` and `logstashMarkerAttributesExcluded`, which in turn take
precedence over the deprecated `captureLogstashMarkerAttributes`. No Logstash marker attributes are
captured when all of these are absent or empty.

Captured Logstash marker attributes may contain sensitive information. Configure included and
excluded patterns to limit the data exported as log attributes.

The Logstash structured argument attributes are selected the same way:

```xml
<appender name="OpenTelemetry" class="io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender">
  <logstashStructuredArgumentAttributesIncluded>request-*</logstashStructuredArgumentAttributesIncluded>
  <logstashStructuredArgumentAttributesExcluded>*-secret</logstashStructuredArgumentAttributesExcluded>
</appender>
```

```java
appender.setLogstashStructuredArgumentAttributes(
    IncludeExclude.builder()
        .setIncluded("request-*")
        .setExcluded("*-secret")
        .build());
```

Logstash structured argument keys and selector patterns are matched case-sensitively. `?` matches
any single character and `*` matches any number of characters, including none, so `*` captures all
Logstash structured argument attributes. Excluded patterns take precedence over included patterns, so
a selector with only excluded patterns captures every Logstash structured argument attribute that it
does not exclude.

Logstash structured argument attributes are captured only when at least one of these settings is
configured. A non-empty `setLogstashStructuredArgumentAttributes(IncludeExclude)` selector takes
precedence over `logstashStructuredArgumentAttributesIncluded` and
`logstashStructuredArgumentAttributesExcluded`, which in turn take precedence over the deprecated
`captureLogstashStructuredArguments`. No Logstash structured argument attributes are captured when
all of these are absent or empty.

Captured Logstash structured argument attributes may contain sensitive information. Configure
included and excluded patterns to limit the data exported as log attributes.

The `otel.event.name` key is supported in key-value pairs (SLF4J 2.x fluent API), MDC entries, Logstash markers (e.g., `Markers.append("otel.event.name", ...)`), and Logstash structured arguments (e.g., `StructuredArguments.keyValue("otel.event.name", ...)`). When present, its value is used as the log event name and is not emitted as an attribute.

[source code attributes]: https://github.com/open-telemetry/semantic-conventions/blob/main/docs/general/attributes.md#source-code-attributes
