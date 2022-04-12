# Ktor Instrumentation

This package contains libraries to help instrument Ktor. Currently, only server instrumentation is supported.

## Initializing server instrumentation

Initialize instrumentation by installing the `KtorServerTracing` feature. You must set the `OpenTelemetry` to use with
the feature.

```kotlin
OpenTelemetry openTelemetry = initializeOpenTelemetryForMe()

embeddedServer(Netty, 8080) {
  install(KtorServerTracing) {
    setOpenTelemetry(openTelemetry)
  }
}
```
