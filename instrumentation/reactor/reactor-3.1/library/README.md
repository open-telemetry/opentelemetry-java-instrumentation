# Library Instrumentation for Project Reactor version 3.1 and higher

Provides OpenTelemetry instrumentation for [Project Reactor](https://projectreactor.io/).

This instrumentation generates spans for each reactive operation.

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest release](https://central.sonatype.com/artifact/io.opentelemetry.instrumentation/opentelemetry-reactor-3.1).

For Maven, add to your `pom.xml` dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-reactor-3.1</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```kotlin
implementation("io.opentelemetry.instrumentation:opentelemetry-reactor-3.1:OPENTELEMETRY_VERSION")
```

### Usage

```java
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

public class ReactorExample {
  public static void main(String[] args) {
    OpenTelemetry openTelemetry = OpenTelemetry.noop();

    ContextPropagationOperator contextPropagationOperator = ContextPropagationOperator.create();
    contextPropagationOperator.registerOnEachOperator();

    Mono<String> mono = Mono.just("Hello, World!");
    Flux<String> flux = Flux.just("Hello", "World");

    mono.subscribe(System.out::println);
    flux.subscribe(System.out::println);
  }
}
```
