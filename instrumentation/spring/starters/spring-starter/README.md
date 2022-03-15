# OpenTelemetry Spring Starter

OpenTelemetry Spring Starter is a starter package that includes the opentelemetry-api, opentelemetry-sdk, opentelemetry-extension-annotations, opentelmetry-logging-exporter, opentelemetry-spring-boot-autoconfigurations and spring framework starters required to setup distributed tracing. Check out [opentelemetry-spring-boot-autoconfigure](../../spring-boot-autoconfigure/README.md#features) for the full list of supported libraries and features.

This version is compatible with Spring Boot 2.0.

## Quickstart

### Add these dependencies to your project.

Replace `OPENTELEMETRY_VERSION` with the latest stable [release](https://search.maven.org/search?q=g:io.opentelemetry).
 - Minimum version: `1.1.0`


### Maven
Add the following dependencies to your `pom.xml` file:

```xml
<dependencies>

  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-spring-starter</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>

</dependencies>
```

### Gradle
Add the following dependencies to your gradle.build file:

```groovy
implementation("io.opentelemetry.instrumentation:opentelemetry-spring-starter:OPENTELEMETRY_VERSION")
```

### Starter Guide

Check out [OpenTelemetry Manual Instrumentation](https://opentelemetry.io/docs/instrumentation/java/manual/) to learn more about
using the OpenTelemetry API to instrument your code.
