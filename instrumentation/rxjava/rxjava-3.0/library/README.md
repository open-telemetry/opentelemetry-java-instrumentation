# Library Instrumentation for RxJava versions 3.0 through 3.1.0

Provides OpenTelemetry context propagation for [RxJava](https://github.com/ReactiveX/RxJava).

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

Enable context propagation once during application startup, before creating subscriptions:

```java
import io.opentelemetry.instrumentation.rxjava.v3_0.TracingAssembly;

TracingAssembly tracingAssembly = TracingAssembly.create();
tracingAssembly.enable();
```

The instrumentation installs RxJava plugin hooks to propagate OpenTelemetry context through
subscriptions and scheduled work. Call `tracingAssembly.disable()` to restore the previous
hooks.
