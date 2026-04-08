# Library Instrumentation for RxJava version 2.0 and higher

Provides OpenTelemetry instrumentation for [RxJava](https://github.com/ReactiveX/RxJava), enabling
context propagation through RxJava's execution model.

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest release](https://central.sonatype.com/artifact/io.opentelemetry.instrumentation/opentelemetry-rxjava-2.0).

For Maven, add to your `pom.xml` dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-rxjava-2.0</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```kotlin
implementation("io.opentelemetry.instrumentation:opentelemetry-rxjava-2.0:OPENTELEMETRY_VERSION")
```

### Usage

Enable RxJava instrumentation by calling `TracingAssembly.enable()` once during application startup.
This will automatically instrument all RxJava operations in your application:

```java
import io.opentelemetry.instrumentation.rxjava.v2_0.TracingAssembly;
import io.reactivex.Observable;
import io.reactivex.Flowable;

public class RxJavaExample {
  public static void main(String[] args) {
    // Enable RxJava instrumentation globally
    TracingAssembly tracingAssembly = TracingAssembly.create();
    tracingAssembly.enable();

    // All RxJava operations will now be automatically instrumented
    Observable<String> observable = Observable.just("Hello", "World");
    Flowable<String> flowable = Flowable.just("Hello", "World");
    ...
  }
}
```
