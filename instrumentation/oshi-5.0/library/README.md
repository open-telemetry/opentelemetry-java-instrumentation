# Library Instrumentation for OSHI version 5.0 and higher

Provides OpenTelemetry instrumentation for [OSHI](https://github.com/oshi/oshi).

Apple Silicon requires OSHI 5.5 or higher.

This instrumentation collects system metrics such as memory usage, network I/O, and disk operations.

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest release](https://central.sonatype.com/artifact/io.opentelemetry.instrumentation/opentelemetry-oshi-5.0).

For Maven, add to your `pom.xml` dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-oshi-5.0</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```kotlin
implementation("io.opentelemetry.instrumentation:opentelemetry-oshi-5.0:OPENTELEMETRY_VERSION")
```

### Usage

```java
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.oshi.v5_0.SystemMetrics;
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
