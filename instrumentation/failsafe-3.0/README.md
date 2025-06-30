# Library Instrumentation for Failsafe version 3.0.1 and higher

Provides OpenTelemetry instrumentation for [Failsafe](https://failsafe.dev/).

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest release](https://search.maven.org/search?q=g:io.opentelemetry.instrumentation%20AND%20a:opentelemetry-failsafe-3.0).

For Maven, add to your `pom.xml` dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-failsafe-3.0</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```groovy
implementation("io.opentelemetry.instrumentation:opentelemetry-failsafe-3.0:OPENTELEMETRY_VERSION")
```

### Usage

The instrumentation library allows creating instrumented `CircuitBreaker` instances for collecting
OpenTelemetry-based metrics.

```java
<R> CircuitBreaker<R> configure(OpenTelemetry openTelemetry, CircuitBreaker<R> circuitBreaker) {
  FailsafeTelemetry failsafeTelemetry = FailsafeTelemetry.create(openTelemetry);
  return failsafeTelemetry.createCircuitBreaker(circuitBreaker, "my-circuit-breaker");
}
```
