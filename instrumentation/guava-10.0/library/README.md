# Library instrumentation for Guava version 10.0 and higher

## Spring Boot starter

Use this library with the [OpenTelemetry Spring Boot starter](https://opentelemetry.io/docs/zero-code/java/spring-boot-starter/)
to keep `@WithSpan` spans open until a returned Guava `ListenableFuture` completes.
The starter does not register Guava support automatically.

First, follow the starter's [annotation setup](https://opentelemetry.io/docs/zero-code/java/spring-boot-starter/annotations/),
including the Spring Boot AOP dependency. Annotated methods must belong to Spring-managed beans and
be called through their Spring proxies, not through self-invocation.

### Dependencies

Add these dependencies alongside the starter and your application's Guava dependency.
Replace `OPENTELEMETRY_VERSION` with the [Guava instrumentation release](https://central.sonatype.com/artifact/io.opentelemetry.instrumentation/opentelemetry-guava-10.0)
matching your starter release, including the `-alpha` suffix.

For Maven:

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

For Gradle:

```kotlin
implementation("io.opentelemetry.instrumentation:opentelemetry-guava-10.0:OPENTELEMETRY_VERSION")
implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations-support:OPENTELEMETRY_VERSION")
```

### Register at startup

Register the strategy once, before starting Spring, so it is available even to annotated methods
called during application initialization:

```java
import io.opentelemetry.instrumentation.api.annotation.support.async.AsyncOperationEndStrategies;
import io.opentelemetry.instrumentation.guava.v10_0.GuavaAsyncOperationEndStrategy;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {
  public static void main(String[] args) {
    AsyncOperationEndStrategies.instance()
        .registerStrategy(GuavaAsyncOperationEndStrategy.create());
    SpringApplication.run(Application.class, args);
  }
}
```

The annotated method must declare `ListenableFuture<T>` or a subtype as its return type.
A method declared to return `Future<T>` or `Object` does not use this strategy, even if it returns
a `ListenableFuture` at runtime.

This opt-in uses the low-level annotation-support registry. Both added artifacts are alpha and
may change incompatibly between releases.
