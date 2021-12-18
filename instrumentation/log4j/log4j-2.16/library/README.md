# Log4j 2 Integration

This module provides Log4j2 extensions related to OpenTelemetry.

To use it, add the module to your application's runtime classpath.

**Maven**

```xml

<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-log4j-appender-2.16</artifactId>
    <version>1.10.0-alpha</version>
    <scope>runtime</scope>
  </dependency>
</dependencies>
```

**Gradle**

```kotlin
dependencies {
  runtimeOnly("io.opentelemetry.instrumentation:opentelemetry-log4j-appender-2.16:1.10.0-alpha")
}
```

## OpenTelemetry Appender

`OpenTelemetryAppender` is a
Log4j2 [appender](https://logging.apache.org/log4j/2.x/manual/appenders.html) that can be used to
forward log events to
the [OpenTelemetry Log SDK](https://github.com/open-telemetry/opentelemetry-java/tree/main/sdk/logs)
.

The following demonstrates how you might configure the appender in your `log4j.xml` configuration:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Configuration status="WARN" packages="io.opentelemetry.instrumentation.log4j.v2_16">
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

Next, associate the `OpenTelemetryAppender` with a `SdkLogEmitterProvider` in your application:

```
SdkLogEmitterProvider logEmitterProvider =
  SdkLogEmitterProvider.builder()
    .setResource(Resource.create(...))
    .addLogProcessor(...)
    .build();
OpenTelemetryLog4j.initialize(logEmitterProvider);
```

**Note:** In order to initialize the `OpenTelemetryAppender` your application must depend on the
OpenTelemetry log sdk (`io.opentelemetry:opentelemetry-sdk-logs`).

In this example Log4j2 logs will be sent to both the console appender and
the `OpenTelemetryAppender`, which will drop the logs until `OpenTelemetryLog4j.initialize(..)` is
called. Once initialized, logs will be emitted to a `LogEmitter` obtained from
the `SdkLogEmitterProvider`.
