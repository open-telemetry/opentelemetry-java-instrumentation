# Library Instrumentation for R2dbc version 1.0 and higher

Provides OpenTelemetry instrumentation for [R2dbc](https://r2dbc.io/).

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest
release](https://mvnrepository.com/artifact/io.opentelemetry.instrumentation/opentelemetry-r2dbc-1.0).

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

If you use R2dbc in a Spring Boot application you can wrap the `ConnectionFactory` using a custom `BeanPostProcessor` implementation:

```java
@Configuration
class R2dbcConfiguration {

  @Bean
  public R2dbcInstrumentingPostProcessor r2dbcInstrumentingPostProcessor(
      OpenTelemetry openTelemetry) {
    return new R2dbcInstrumentingPostProcessor(openTelemetry);
  }
}

class R2dbcInstrumentingPostProcessor implements BeanPostProcessor {

  private final OpenTelemetry openTelemetry;

  R2dbcInstrumentingPostProcessor(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) {
    if (!(bean instanceof ConnectionFactory)) {
      return bean;
    }
    ConnectionFactory connectionFactory = (ConnectionFactory) bean;
    return R2dbcTelemetry.create(openTelemetry)
        .wrapConnectionFactory(connectionFactory, getConnectionFactoryOptions(connectionFactory));
  }

  private static ConnectionFactoryOptions getConnectionFactoryOptions(ConnectionFactory connectionFactory) {
    OptionsCapableConnectionFactory optionsCapableConnectionFactory =
        OptionsCapableConnectionFactory.unwrapFrom(connectionFactory);
    if (optionsCapableConnectionFactory != null) {
      return optionsCapableConnectionFactory.getOptions();
    } else {
      // in practice should never happen
      // fall back to empty options; or reconstruct them from the R2dbcProperties
      return ConnectionFactoryOptions.builder().build();
    }
  }
}
```
