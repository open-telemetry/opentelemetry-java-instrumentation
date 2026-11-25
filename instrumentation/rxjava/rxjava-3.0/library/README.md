# Library Instrumentation for RxJava version 3.0 to 3.1.0

Provides OpenTelemetry instrumentation for [RxJava](https://github.com/ReactiveX/RxJava), enabling
context propagation through RxJava's execution model.

For RxJava 3.1.1 and higher, use the [3.1.1 instrumentation](../../rxjava-3.1.1/library/README.md).

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest release](https://central.sonatype.com/artifact/io.opentelemetry.instrumentation/opentelemetry-rxjava-3.0).

For Maven, add to your `pom.xml` dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-rxjava-3.0</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```kotlin
implementation("io.opentelemetry.instrumentation:opentelemetry-rxjava-3.0:OPENTELEMETRY_VERSION")
```

### Usage

Enable RxJava instrumentation by calling `TracingAssembly.enable()` once during application startup,
before assembling RxJava pipelines:

```java
import io.opentelemetry.instrumentation.rxjava.v3_0.TracingAssembly;

TracingAssembly tracingAssembly = TracingAssembly.create();
tracingAssembly.enable();
```
