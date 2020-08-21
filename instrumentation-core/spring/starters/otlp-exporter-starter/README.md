# OpenTelemetry OTLP Exporter Starter

The OpenTelemetry OTLP Exporter Starter for Java is a starter package that includes packages required to enable tracing using OpenTelemetry. It also provides the [opentelemetry-exporters-otlp](https://github.com/open-telemetry/opentelemetry-java/tree/master/exporters/otlp) dependency and corresponding auto-configuration.  Check out [opentelemetry-spring-boot-autoconfigure](../../spring-boot-autoconfigure/README.md#features) for the list of supported libraries and features.

## Quickstart

### Add these dependencies to your project.

Replace `OPENTELEMETRY_VERSION` with the latest stable [release](https://search.maven.org/search?q=g:io.opentelemetry).
 - Minimum version: `0.7.0`
 - Note: You may need to include our bintray maven repository to your build file: `https://dl.bintray.com/open-telemetry/maven/`. As of August 2020 the latest opentelemetry-java-instrumentation artifacts are not published to maven-central. Please check the [releasing](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/master/RELEASING.md) doc for updates to this process.


For Maven add to your `pom.xml`:

```xml
<dependencies>

  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-otlp-exporter-starter</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>

</dependencies>
```

For Gradle add to your dependencies:

```groovy
implementation 'io.opentelemetry.instrumentation:opentelemetry-otlp-exporter-starter:OPENTELEMETRY_VERSION'
```

### Starter Guide

Check out the opentelemetry-api [quick start](https://github.com/open-telemetry/opentelemetry-java/blob/master/QUICKSTART.md) to learn more about OpenTelemetry instrumentation.
