# Library Instrumentation for R2dbc version 1.0 and higher

Provides OpenTelemetry instrumentation for [R2dbc](https://r2dbc.io/).

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest
release](https://search.maven.org/search?q=g:io.opentelemetry.instrumentation%20AND%20a:opentelemetry-r2dbc-1.0).

For Maven, add to your `pom.xml` dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-r2dbc-1.0</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```groovy
implementation("io.opentelemetry.instrumentation:opentelemetry-r2dbc-1.0:OPENTELEMETRY_VERSION")
```

### Usage

The instrumentation library provides a R2dbc `ProxyConnectionFactory` that gets wrapped around the original
`ConnectionFactory`.

```java
ConnectionFactory wrapWithProxyFactory(OpenTelemetry openTelemetry, ConnectionFactory originalFactory, ConnectionFactoryOptions factoryOptions) {
  return R2dbcTelemetryBuilder
    .create(openTelemetry)
    .wrapConnectionFactory(originalFactory, factoryOptions);
}
```

If you use R2dbc in a Spring application you can wrap the `ConnectionFactory` in the `ConnectionFactoryInitializer` Bean:
```java
@Bean
ConnectionFactoryInitializer initializer(OpenTelemetry openTelemetry, ConnectionFactory connectionFactory) {
    ConnectionFactoryInitializer initializer = new ConnectionFactoryInitializer();
    ConnectionFactoryOptions factoryOptions = ConnectionFactoryOptions.parse("r2dbc:mariadb://localhost:3306/db");
    initializer.setConnectionFactory(wrapWithProxyFactory(openTelemetry, connectionFactory, factoryOptions));
    return initializer;
}
```
