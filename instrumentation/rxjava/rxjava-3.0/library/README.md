# Library Instrumentation for RxJava 3.0

## Dependencies

Add this dependency to your application, replacing `OPENTELEMETRY_VERSION` with the
[latest release](https://central.sonatype.com/artifact/io.opentelemetry.instrumentation/opentelemetry-rxjava-3.0):

```kotlin
implementation("io.opentelemetry.instrumentation:opentelemetry-rxjava-3.0:OPENTELEMETRY_VERSION")
```

## Usage

Enable context propagation once during application startup, before creating subscriptions:

```java
import io.opentelemetry.instrumentation.rxjava.v3_0.TracingAssembly;

TracingAssembly tracingAssembly = TracingAssembly.create();
tracingAssembly.enable();
```

The instrumentation installs RxJava plugin hooks to propagate OpenTelemetry context through
subscriptions and scheduled work. Call `tracingAssembly.disable()` to restore the previous
hooks. For RxJava 3.1.1 and higher, use the [3.1.1 instrumentation](../../rxjava-3.1.1/library)
instead.
