# Manual Instrumentation for JDBC

Provides OpenTelemetry instrumentation for [Java JDBC API](https://docs.oracle.com/javase/8/docs/technotes/guides/jdbc/).

## Quickstart

### Add these dependencies to your project.

Replace `OPENTELEMETRY_VERSION` with the latest stable [release](https://mvnrepository.com/artifact/io.opentelemetry).
`Minimum version: 1.4.0`

For Maven add to your `pom.xml`:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-jdbc</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle add to your dependencies:

```groovy
implementation("io.opentelemetry.instrumentation:opentelemetry-jdbc:OPENTELEMETRY_VERSION")
```

##### Usage

There two possible modes to activate OpenTelemetry tracing with JDBC. The first one is requires
to change the connection URL and switch to use a special OpenTelemetry driver, when another way
is requires minimal changes in your application without the needs to change the connection URL but
requires to remove explicit driver selection.

### Non-interceptor mode

1. Activate tracing for JDBC connections by setting `jdbc:otel:` prefix to the JDBC URL:

```
jdbc:otel:h2:mem:test
```

2. Set the driver class to `io.opentelemetry.instrumentation.jdbc.OpenTelemetryDriver` and
   initialize the driver with:

```java
Class.forName("io.opentelemetry.instrumentation.jdbc.OpenTelemetryDriver");
```

### Interceptor mode

This mode can be useful for activating tracing for all JDBC connections without modifying the URL.

In "interceptor mode", the `OpenTelemetryDriver` will intercept calls to
`DriverManager.getConnection(url,...)` for all URLs. The `OpenTelemetryDriver` provides connections
to the `DriverManager` that are instrumented. Please note that the `OpenTelemetryDriver` must be
registered before the underlying driver, It's recommended to turn on "interceptor mode" in the first place.

For most applications:

```java
public static void main(String[] args) {
   io.opentelemetry.instrumentation.jdbc.OpenTelemetryDriver.setInterceptorMode(true);

   // ...
}

```

For web applications based on Servlet API:

```java
public void contextInitialized(ServletContextEvent event) {
   io.opentelemetry.instrumentation.jdbc.OpenTelemetryDriver.setInterceptorMode(true);

   // ...
}
```

Sometimes it's also required to register the OpenTelemetry driver as a first driver in the
DriverManager list, otherwise another driver can be selected at the time of initialization.

To fix this problem it requires to call `OpenTelemetryDriver.ensureRegisteredAsTheFirstDriver()`
method along with `setInterceptorMode(true)`.

Please note drivers like Oracle JDBC may fail since it's destroyed forever after deregistration.
