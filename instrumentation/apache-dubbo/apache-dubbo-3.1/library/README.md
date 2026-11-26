# Library Instrumentation for Apache Dubbo version 3.1 and higher

Use your own `OpenTelemetry` instance to instrument [Apache Dubbo](https://github.com/apache/dubbo).
The standalone library requires Dubbo 3.1 or later. The Java agent supports Dubbo 2.7 and later.

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest release](https://central.sonatype.com/artifact/io.opentelemetry.instrumentation/opentelemetry-apache-dubbo-3.1).

For Maven, add to your `pom.xml` dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-apache-dubbo-3.1</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```kotlin
implementation("io.opentelemetry.instrumentation:opentelemetry-apache-dubbo-3.1:OPENTELEMETRY_VERSION")
```

### Usage

Call `instrument` before initializing the reference or exporting the service:

```java
import io.opentelemetry.instrumentation.apachedubbo.v3_1.DubboTelemetry;

DubboTelemetry telemetry = DubboTelemetry.create(openTelemetry);
telemetry.instrument(referenceConfig);
telemetry.instrument(serviceConfig);
```

Only the references and services you instrument use these tracing filters. You do not need to
register a Dubbo extension or set `GlobalOpenTelemetry`.
