# Automatic Library Instrumentation for AWS SDK for Java 2.2 and Higher

## Dependencies

Add this dependency to your application, replacing `OPENTELEMETRY_VERSION` with the
[latest release](https://central.sonatype.com/artifact/io.opentelemetry.instrumentation/opentelemetry-aws-sdk-2.2-autoconfigure):

```kotlin
implementation("io.opentelemetry.instrumentation:opentelemetry-aws-sdk-2.2-autoconfigure:OPENTELEMETRY_VERSION")
```

## Usage

Include this artifact on the application classpath and initialize the global
`OpenTelemetry` instance before creating RPC or SDK clients and servers.

AWS SDK v2 discovers the tracing interceptor through its global execution-interceptor SPI.
No per-client interceptor registration is needed. For the additional client wrapping
required by SQS and asynchronous Bedrock Runtime, see the
[plain library usage](../../aws-sdk-2.2/library#usage).
