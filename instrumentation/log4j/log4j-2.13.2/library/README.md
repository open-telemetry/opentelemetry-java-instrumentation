# Log4j 2 Integration

This module integrates instrumentation with Log4j 2 by injecting the trace ID and span ID from a
mounted span into
Log4j's [context data](https://logging.apache.org/log4j/2.x/manual/thread-context.html).

**Note**: Depending on your application, you may run into a [critical bug](https://issues.apache.org/jira/browse/LOG4J2-2838)
with Log4j 2.13.2. If log messages show a `NullPointerException` when adding this instrumentation,
please update to 2.13.3 or higher. The only change between 2.13.2 and 2.13.3 is the fix to this
issue.

To use it, just add the module to your application's runtime classpath.

**Maven**

```xml

<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-log4j-2.13.2</artifactId>
    <version>0.17.0-alpha</version>
    <scope>runtime</scope>
  </dependency>
</dependencies>
```

**Gradle**

```kotlin
dependencies {
  runtimeOnly("io.opentelemetry.instrumentation:opentelemetry-log4j-2.13.2:0.17.0-alpha")
}
```

Log4j will automatically pick up our integration and will have these keys added to the context when
a log statement is made when a span is active.

- `trace_id`
- `span_id`
- `trace_flags`

You can use these keys when defining an appender in your `log4j.xml` configuration, for example

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Configuration>
  <Appenders>
    <Console name="Console" target="SYSTEM_OUT">
      <PatternLayout
          pattern="%d{HH:mm:ss.SSS} [%t] %-5level %logger{36} trace_id=%X{trace_id} span_id=%X{span_id} trace_flags=%X{trace_flags} - %msg%n"/>
    </Console>
  </Appenders>
  <Loggers>
    <Root>
      <AppenderRef ref="Console" level="All"/>
    </Root>
  </Loggers>
</Configuration>
```
