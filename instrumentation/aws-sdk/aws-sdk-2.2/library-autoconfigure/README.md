# Autoconfigured Library Instrumentation for AWS SDK for Java version 2.2 and higher

Provides OpenTelemetry instrumentation for the [AWS SDK for Java v2](https://github.com/aws/aws-sdk-java-v2)
that registers itself with every client.

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest release](https://central.sonatype.com/artifact/io.opentelemetry.instrumentation/opentelemetry-aws-sdk-2.2-autoconfigure).

For Maven, add to your `pom.xml` dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-aws-sdk-2.2-autoconfigure</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
    <scope>runtime</scope>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```kotlin
runtimeOnly("io.opentelemetry.instrumentation:opentelemetry-aws-sdk-2.2-autoconfigure:OPENTELEMETRY_VERSION")
```

### Usage

Register your SDK with `GlobalOpenTelemetry` before building any AWS SDK clients. The AWS SDK
loads the tracing execution interceptor automatically.
See the [AWS SDK instrumentation settings](../../README.md) for configuration.

Autoconfigure registers the interceptor but does not wrap SQS or asynchronous Bedrock Runtime clients.
See [how to wrap these clients](../library/README.md#wrap-sqs-and-asynchronous-bedrock-runtime-clients)
with or without autoconfigure.
