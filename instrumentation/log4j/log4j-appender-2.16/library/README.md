# Log4j2 Appender

This module provides a Log4j2 [appender](https://logging.apache.org/log4j/2.x/manual/appenders.html)
which forwards Log4j2 log events to
the [OpenTelemetry Log SDK](https://github.com/open-telemetry/opentelemetry-java/tree/main/sdk/logs)
.

To use it, add the following modules to your application's classpath.

Replace `OPENTELEMETRY_VERSION` with the latest
stable [release](https://search.maven.org/search?q=g:io.opentelemetry.instrumentation).

**Maven**

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-log4j-appender-2.16</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
    <scope>runtime</scope>
  </dependency>
  <dependency>
    <!-- The SDK appender is required to configure the appender with the OpenTelemetry Log SDK -->
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-instrumentation-sdk-appender</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

**Gradle**

```kotlin
dependencies {
  runtimeOnly("io.opentelemetry.instrumentation:opentelemetry-log4j-appender-2.16:OPENTELEMETRY_VERSION")
  // The SDK appender is required to configure the appender with the OpenTelemetry Log SDK
  implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-sdk-appender:OPENTELEMETRY_VERSION")
}
```

The following demonstrates how you might configure the appender in your `log4j2.xml` configuration:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Configuration status="WARN" packages="io.opentelemetry.instrumentation.log4j.appender.v2_16">
  <Appenders>
    <Console name="Console" target="SYSTEM_OUT">
      <PatternLayout
          pattern="%d{HH:mm:ss.SSS} [%t] %-5level %logger{36} traceId: %X{trace_id} spanId: %X{span_id} flags: %X{trace_flags} - %msg%n"/>
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

Next, associate the `OpenTelemetryAppender` configured via `log4j2.xml` with
a `SdkLogEmitterProvider` in your application:

```
SdkLogEmitterProvider logEmitterProvider =
  SdkLogEmitterProvider.builder()
    .setResource(Resource.create(...))
    .addLogProcessor(...)
    .build();
GlobalLogEmitterProvider.set(DelegatingLogEmitterProvider.from(logEmitterProvider));
```

In this example Log4j2 log events will be sent to both the console appender and
the `OpenTelemetryAppender`, which will drop the logs until `GlobalLogEmitterProvider.set(..)` is
called. Once initialized, logs will be emitted to a `LogEmitter` obtained from
the `SdkLogEmitterProvider`.
