# Automatic Library Instrumentation for AWS SDK for Java 1.11 and Higher

## Dependencies

Add this dependency to your application, replacing `OPENTELEMETRY_VERSION` with the
[latest release](https://central.sonatype.com/artifact/io.opentelemetry.instrumentation/opentelemetry-aws-sdk-1.11-autoconfigure):

```kotlin
implementation("io.opentelemetry.instrumentation:opentelemetry-aws-sdk-1.11-autoconfigure:OPENTELEMETRY_VERSION")
```

## Usage

Include this artifact on the application classpath and initialize the global
`OpenTelemetry` instance before creating RPC or SDK clients and servers.

AWS SDK v1 discovers the tracing request handler through its global request-handler SPI.
No per-client handler registration is needed. To instrument only selected clients, use
the [plain library](../../aws-sdk-1.11/library) instead.
