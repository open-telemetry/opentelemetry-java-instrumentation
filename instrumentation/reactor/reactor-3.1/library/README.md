# Library Instrumentation for Project Reactor version 3.1 and higher

Provides OpenTelemetry instrumentation for [Project Reactor](https://projectreactor.io/), enabling
context propagation through Reactor's execution model.

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
    ContextPropagationOperator contextPropagationOperator = ContextPropagationOperator.create();
    contextPropagationOperator.registerOnEachOperator();

    Mono<String> mono = Mono.just("Hello, World!");
    Flux<String> flux = Flux.just("Hello", "World");
    ...
  }
}
```

### Explicit context and subscription capture

An explicitly stored OpenTelemetry context is authoritative, including `Context.root()`, which
clears the active context during signals. Without an explicit context, the hooks capture the
subscription context; capturing root leaves contexts activated by application code during signals
visible. To leave signal-time contexts untouched even when subscribing under an active context,
disable subscription capture:

```java
// Clear the active OpenTelemetry context during signals.
flux.subscriberContext(reactorContext ->
    ContextPropagationOperator.storeOpenTelemetryContext(
        reactorContext, io.opentelemetry.context.Context.root()));

// Keep the context active when each signal is processed, without capturing subscription context.
flux.subscriberContext(ContextPropagationOperator::withoutSubscriptionContextCapture);
```

On Reactor 3.4 and later, use `contextWrite` instead of `subscriberContext`. Storing an explicit
context after disabling capture replaces the no-capture choice.

### Operation/application boundary

Use the three-argument overload to isolate an operation's context from application callbacks:

```java
Mono<Response> response = ContextPropagationOperator.runWithContext(
    Mono.defer(() -> client.exchange(request)), operationContext, applicationContext);
```

Source subscription, upstream demand and cancellation run under `operationContext`. Callbacks to
the immediate downstream subscriber run under `applicationContext`, including root. Downstream
tracing subscribers retain their own context policy; the helper does not carry an inner
application context with values across fan-in operators. Unrelated Reactor context entries are
preserved. The helper neither starts nor ends spans.

These overloads do not register global hooks. Their direct subscription, demand, cancellation and
callback scopes work without hooks; propagation through arbitrary asynchronous upstream operators
still requires `registerOnEachOperator()`. The two-argument overloads retain their hook-dependent
propagation behavior and do not establish an application callback boundary.
