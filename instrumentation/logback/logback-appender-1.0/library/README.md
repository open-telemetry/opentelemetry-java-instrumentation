# Logback Appender

This module provides a Logback [appender](https://logback.qos.ch/manual/appenders.html) which
forwards Logback log events to the
[OpenTelemetry Log SDK](https://github.com/open-telemetry/opentelemetry-java/tree/main/sdk/logs).

To use it, add the following modules to your application's classpath.

Replace `OPENTELEMETRY_VERSION` with the latest
stable [release](https://search.maven.org/search?q=g:io.opentelemetry.instrumentation).

**Maven**

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-logback-appender-1.0</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

**Gradle**

```kotlin
dependencies {
  runtimeOnly("io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0:OPENTELEMETRY_VERSION")
}
```

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

Next, associate the `OpenTelemetryAppender` configured via `logback.xml` with
an `SdkLogEmitterProvider` in your application:

```
SdkLogEmitterProvider logEmitterProvider =
  SdkLogEmitterProvider.builder()
    .setResource(Resource.create(...))
    .addLogProcessor(...)
    .build();
OpenTelemetryAppender.setSdkLogEmitterProvider(logEmitterProvider);
```

In this example Logback log events will be sent to both the console appender and
the `OpenTelemetryAppender`, which will drop the logs until
`OpenTelemetryAppender.setSdkLogEmitterProvider(..)` is called. Once initialized, logs will be
emitted to a `LogEmitter` obtained from the `SdkLogEmitterProvider`.
