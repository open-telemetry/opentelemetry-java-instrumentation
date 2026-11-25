# Library Instrumentation for AWS SDK for Java version 2.2 and higher

Provides automatic OpenTelemetry instrumentation for the [AWS SDK for Java v2](https://github.com/aws/aws-sdk-java-v2).

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
loads the tracing execution interceptor automatically; you don't need to add it to each client.
See the [AWS SDK instrumentation settings](../../README.md) for configuration.

For SQS and asynchronous Bedrock Runtime, also [wrap the clients](../library/README.md#wrap-sqs-and-asynchronous-bedrock-runtime-clients).
