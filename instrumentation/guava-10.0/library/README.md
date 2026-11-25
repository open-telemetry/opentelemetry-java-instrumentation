# Library Instrumentation for Guava 10.0 and Higher

## Dependencies

Add this dependency to your application, replacing `OPENTELEMETRY_VERSION` with the
[latest release](https://central.sonatype.com/artifact/io.opentelemetry.instrumentation/opentelemetry-guava-10.0):

```kotlin
implementation("io.opentelemetry.instrumentation:opentelemetry-guava-10.0:OPENTELEMETRY_VERSION")
implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations-support:OPENTELEMETRY_VERSION")
```

## Usage

Register the asynchronous span-ending strategy once during application startup:

```java
import io.opentelemetry.instrumentation.api.annotation.support.async.AsyncOperationEndStrategies;
import io.opentelemetry.instrumentation.guava.v10_0.GuavaAsyncOperationEndStrategy;

AsyncOperationEndStrategies.instance().registerStrategy(GuavaAsyncOperationEndStrategy.create());
```

This lets instrumentation using the asynchronous annotation support keep a span open until
its returned Guava `ListenableFuture` completes. It does not instrument arbitrary Guava calls
by itself. To capture the experimental cancellation attribute, create the strategy with
`GuavaAsyncOperationEndStrategy.builder().setCaptureExperimentalSpanAttributes(true).build()`.
