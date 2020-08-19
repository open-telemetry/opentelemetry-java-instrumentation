# OpenTelemetry Spring Starter

The OpenTelemetry Spring Starter for Java is a starter package that includes packages required to enable tracing using OpenTelemetry. Check out [opentelemetry-spring-boot-autoconfigure](../../spring-boot-autoconfigure/README.md#features) for the list of supported libraries and features.

This version is compatible with Spring Boot 2.0.

## Quickstart

### Add these dependencies to your project.

Replace `OPENTELEMETRY_VERSION` with the latest stable [release](https://mvnrepository.com/artifact/io.opentelemetry).
 - `Minimum version: 0.8.0`

For Maven add to your `pom.xml`:

```xml
<dependencies>

  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-spring-starter</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>

</dependencies>
```

For Gradle add to your dependencies:

```groovy
implementation 'io.opentelemetry.instrumentation:opentelemetry-spring-starter:OPENTELEMETRY_VERSION'
```

### Starter Guide

Check out the opentelemetry-api [quick start](https://github.com/open-telemetry/opentelemetry-java/blob/master/QUICKSTART.md) to learn more about OpenTelemetry instrumentation.
