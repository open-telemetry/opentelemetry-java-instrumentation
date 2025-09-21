# Library Instrumentation for OSHI version 5.3.1 and higher

Provides OpenTelemetry instrumentation for [OSHI](https://github.com/oshi/oshi).

This instrumentation collects system metrics such as memory usage, network I/O, and disk operations.

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest release](https://central.sonatype.com/artifact/io.opentelemetry.instrumentation/opentelemetry-oshi).

For Maven, add to your `pom.xml` dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-oshi</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```kotlin
implementation("io.opentelemetry.instrumentation:opentelemetry-oshi:OPENTELEMETRY_VERSION")
```

### Usage

```java
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.oshi.SystemMetrics;
import java.util.List;

// Get an OpenTelemetry instance
OpenTelemetry openTelemetry = ...;

List<AutoCloseable> observables = SystemMetrics.registerObservers(openTelemetry);

// The observers will automatically collect and export system metrics
// Close the observables when shutting down your application
observables.forEach(observable -> {
    try {
        observable.close();
    } catch (Exception e) {
        // Handle exception
    }
});
```
