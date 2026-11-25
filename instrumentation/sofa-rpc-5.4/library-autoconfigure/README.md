# Automatic Library Instrumentation for SOFARPC 5.4 and Higher

## Dependencies

Add this dependency to your application, replacing `OPENTELEMETRY_VERSION` with the
[latest release](https://central.sonatype.com/artifact/io.opentelemetry.instrumentation/opentelemetry-sofa-rpc-5.4-autoconfigure):

```kotlin
implementation("io.opentelemetry.instrumentation:opentelemetry-sofa-rpc-5.4-autoconfigure:OPENTELEMETRY_VERSION")
```

## Usage

Include this artifact on the application classpath and initialize the global
`OpenTelemetry` instance before creating RPC or SDK clients and servers.

SOFARPC discovers and activates the tracing filters through its filter extension SPI.
No per-client or per-provider filter registration is needed. To configure the filters
explicitly with your own OpenTelemetry instance, use the
[plain library](../../sofa-rpc-5.4/library) instead.
