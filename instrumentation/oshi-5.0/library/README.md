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

## Migrating to 3.0

The deprecated `SystemMetrics.registerObservers(Meter)` overload has been removed.
Pass the owning `OpenTelemetry` instance instead so the instrumentation can set its
scope, version, and schema:

```java
// Before
SystemMetrics.registerObservers(openTelemetry.getMeter("application"));

// After
List<AutoCloseable> observables = SystemMetrics.registerObservers(openTelemetry);
```

Continue closing the returned observers when they are no longer needed.
The deprecated `ProcessMetrics` class has also been removed. See the
[system metric and process metric migration notes](../README.md#migrating-to-30).
