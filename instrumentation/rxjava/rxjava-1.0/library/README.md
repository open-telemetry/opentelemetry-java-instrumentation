# Library Instrumentation for RxJava version 1.0 and higher

Provides OpenTelemetry instrumentation utilities for [RxJava](https://github.com/ReactiveX/RxJava/tree/1.x),
enabling context propagation through RxJava's execution model.

**Note**: This library is primarily designed for use by other OpenTelemetry instrumentation
libraries and framework integrations, not for direct end-user application usage.

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest release](https://central.sonatype.com/artifact/io.opentelemetry.instrumentation/opentelemetry-rxjava-1.0).

For Maven, add to your `pom.xml` dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-rxjava-1.0</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```kotlin
implementation("io.opentelemetry.instrumentation:opentelemetry-rxjava-1.0:OPENTELEMETRY_VERSION")
```

### Usage

This library provides the `TracedOnSubscribe` class for instrumenting RxJava Observables with
OpenTelemetry spans:

```java
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.rxjava.v1_0.TracedOnSubscribe;
import rx.Observable;

public class RxJavaExample {
  public static void main(String[] args) {
    // Get an OpenTelemetry instance
    OpenTelemetry openTelemetry = ...;

    // Create an instrumenter for your specific request type
    Instrumenter<String, Void> instrumenter = Instrumenter.<String, Void>builder(
        openTelemetry,
        "instrumentation-name",
        request -> "operation-name")
        .buildInstrumenter();

    // Create a regular Observable
    Observable<String> originalObservable = Observable.just("Hello", "World");

    // Wrap it with tracing
    Observable<String> tracedObservable = Observable.create(
        new TracedOnSubscribe<>(originalObservable, instrumenter, "request-context"));

    // Subscribe to the traced observable
    tracedObservable.subscribe(System.out::println);
  }
}
```
