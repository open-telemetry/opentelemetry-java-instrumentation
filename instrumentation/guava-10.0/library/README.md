# Library Instrumentation for Guava version 10.0 and higher

Provides OpenTelemetry instrumentation for [Guava](https://github.com/google/guava), enabling
asynchronous span completion for `ListenableFuture` return values.

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest release](https://central.sonatype.com/artifact/io.opentelemetry.instrumentation/opentelemetry-guava-10.0).

For Maven, add to your `pom.xml` dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-guava-10.0</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-instrumentation-annotations-support</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```kotlin
implementation("io.opentelemetry.instrumentation:opentelemetry-guava-10.0:OPENTELEMETRY_VERSION")
implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations-support:OPENTELEMETRY_VERSION")
```

### Usage

Register the asynchronous span-ending strategy once during application startup:

```java
import io.opentelemetry.instrumentation.api.annotation.support.async.AsyncOperationEndStrategies;
import io.opentelemetry.instrumentation.guava.v10_0.GuavaAsyncOperationEndStrategy;

AsyncOperationEndStrategies.instance().registerStrategy(GuavaAsyncOperationEndStrategy.create());
```

This lets instrumentation using the asynchronous annotation support keep a span open until
its returned Guava `ListenableFuture` completes. It does not instrument arbitrary Guava calls
by itself.
