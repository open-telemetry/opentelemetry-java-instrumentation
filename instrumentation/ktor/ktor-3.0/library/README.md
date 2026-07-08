# Library Instrumentation for Ktor version 3.0 and higher

This package contains libraries to help instrument Ktor.
Server and client instrumentations are supported.

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest
release](https://central.sonatype.com/artifact/io.opentelemetry.instrumentation/opentelemetry-ktor-3.0).

For Maven, add to your `pom.xml` dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-ktor-3.0</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```groovy
implementation("io.opentelemetry.instrumentation:opentelemetry-ktor-3.0:OPENTELEMETRY_VERSION")
```

## Usage

## Initializing server instrumentation

Initialize instrumentation by installing the `KtorServerTelemetry` feature. Make sure that no other
logging plugin is installed before this.
You must set the `OpenTelemetry` to use with the feature.

```kotlin
val openTelemetry: OpenTelemetry = ...

embeddedServer(Netty, 8080) {
  install(KtorServerTelemetry) {
    setOpenTelemetry(openTelemetry)
  }
}
```

## Initializing client instrumentation

Initialize instrumentation by installing the `KtorClientTelemetry` feature. You must set the
`OpenTelemetry` to use with
the feature.

```kotlin
val openTelemetry: OpenTelemetry = ...

val client = HttpClient {
  install(KtorClientTelemetry) {
    setOpenTelemetry(openTelemetry)
  }
}
```

### Setting attributes on the client span

The client span is stored on the `HttpClientCall` and can be reached from a response. This is the
supported way to enrich the client span with the library instrumentation, from inside a
`HttpStatement.execute { }` block, where the client span is not the ambient `Context`:

```kotlin
import io.opentelemetry.instrumentation.ktor.v3_0.withClientSpan

client.prepareGet("https://example.com").execute { response ->
  response.call.withClientSpan {
    setAttribute("example.attribute", "value")
  }
  // ...
}
```

`HttpClientCall.getOpenTelemetryContext()` is also available if you need the raw `Context` (for
example to make it current via `asContextElement()`). Both return `null`/no-op if no span was
created. e.g. if instrumentation is disabled, or the client span was suppressed because a client
span was already active.

The client span ends when the call completes, so mutate it while the call is still in progress
(within the `execute` block); attribute changes after the span has ended are no-ops.

> [!NOTE]
> This `HttpClientCall` approach also works under the OpenTelemetry Java agent (the agent bridges
> the context it stores on the call). Under the agent the client span is additionally available as
> the ambient `Context` inside the `execute` block, so `Span.current()` works there too — see the
> [javaagent README](../javaagent/README.md#setting-attributes-on-the-client-span). Prefer this
> `HttpClientCall` approach if your code must run in both modes.
