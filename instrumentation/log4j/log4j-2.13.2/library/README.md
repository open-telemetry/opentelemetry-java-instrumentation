# Log4j 2 Integration

This module integrates instrumentation with Log4j 2 by injecting the trace ID and span ID from a
mounted span into Log4j's [context data](https://logging.apache.org/log4j/2.x/manual/thread-context.html).

To use it, just add the module to your application's runtime classpath.

**Maven**

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-log4j-2.13.2</artifactId>
    <version>0.7.0-SNAPSHOT</version>
    <scope>runtime</scope>
  </dependency>
</dependencies>
```

**Gradle**

```kotlin
dependencies {
  runtimeOnly("io.opentelemetry.instrumentation:opentelemetry-log4j-2.13.2:0.7.0-SNAPSHOT")
}
```

Log4j will automatically pick up our integration and will have these keys added to the context when
a log statement is made when a span is active.

- `traceId`
- `spanId`
- `sampled`

You can use these keys when defining an appender in your `log4j.xml` configuration, for example

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Configuration>
  <Appenders>
    <Console name="Console" target="SYSTEM_OUT">
      <PatternLayout pattern="%d{HH:mm:ss.SSS} [%t] %-5level %logger{36} traceId: %X{traceId} spanId: %X{spanId} - %msg%n" />
    </Console>
  </Appenders>
  <Loggers>
    <Root>
      <AppenderRef ref="Console" level="All" />
    </Root>
  </Loggers>
</Configuration>
```
