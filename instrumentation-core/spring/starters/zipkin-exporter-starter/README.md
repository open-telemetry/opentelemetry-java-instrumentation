# OpenTelemetry Zipkin Exporter Starter

The OpenTelemetry Zipkin Exporter Starter for Java is a starter package that includes packages required to enable tracing using opentelemetry. It also provides the [opentelemetry-exporters-zipkin](https://github.com/open-telemetry/opentelemetry-java/tree/master/exporters/zipkin) dependency and corresponding autoconfigurations.  Check out [opentelemetry-spring-autoconfigure](../spring-boot-autoconfigure/README.md#features) for the list of supported libraries and features.

## Quickstart

### Add these dependencies to your project.

Replace `OPENTELEMETRY_VERSION` with the latest stable [release](https://mvnrepository.com/artifact/io.opentelemetry).
 - `Minimum version: 0.8.0`

For Maven add to your `pom.xml`:

```xml
<dependencies>

  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-zipkin-exporter-starter</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>

</dependencies>
```

For Gradle add to your dependencies:

```groovy
implementation 'io.opentelemetry.instrumentation:opentelemetry-zipkin-exporter-starter:OPENTELEMETRY_VERSION'
```

### Starter Guide

Check out the opentelemetry-api [quick start](https://github.com/open-telemetry/opentelemetry-java/blob/master/QUICKSTART.md) to learn more about OpenTelemetry instrumentation.
