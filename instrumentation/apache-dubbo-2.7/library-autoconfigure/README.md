# Library Instrumentation for Apache Dubbo 2.7 and Higher

## Dependencies

Add this dependency to your application, replacing `OPENTELEMETRY_VERSION` with the
[latest release](https://central.sonatype.com/artifact/io.opentelemetry.instrumentation/opentelemetry-apache-dubbo-2.7):

```kotlin
implementation("io.opentelemetry.instrumentation:opentelemetry-apache-dubbo-2.7:OPENTELEMETRY_VERSION")
```

## Usage

Include this artifact on the application classpath and initialize the global
`OpenTelemetry` instance before creating RPC or SDK clients and servers.

Dubbo discovers and activates the client and server filters through its extension SPI.
They record RPC telemetry and propagate trace context without manually registering filters.
