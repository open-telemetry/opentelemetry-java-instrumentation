# Library Instrumentation for AWS SDK for Java version 1.11 and higher

Provides automatic OpenTelemetry instrumentation for the [AWS SDK for Java v1](https://aws.amazon.com/sdk-for-java/).

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest release](https://central.sonatype.com/artifact/io.opentelemetry.instrumentation/opentelemetry-aws-sdk-1.11-autoconfigure).

For Maven, add to your `pom.xml` dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-aws-sdk-1.11-autoconfigure</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
    <scope>runtime</scope>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```kotlin
runtimeOnly("io.opentelemetry.instrumentation:opentelemetry-aws-sdk-1.11-autoconfigure:OPENTELEMETRY_VERSION")
```

### Usage

Register your SDK with `GlobalOpenTelemetry` before building any AWS SDK clients. The AWS SDK
loads the tracing request handler automatically; you don't need to add it to each client.
See the [AWS SDK instrumentation settings](../../README.md) for configuration.

To instrument only selected clients, use the [library with explicit registration](../library/README.md)
instead.
