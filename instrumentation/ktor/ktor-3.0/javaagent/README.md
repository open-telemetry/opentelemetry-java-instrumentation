# Auto-instrumentation for Ktor version 3.0 and higher

Provides OpenTelemetry auto-instrumentation for [Ktor](https://ktor.io/) server and client.

The agent installs the same `KtorServerTelemetry` / `KtorClientTelemetry` plugins as the
[library instrumentation](../library/README.md), so no code changes are required. See the library
README for configuration details.

## Setting attributes on the client span

There are two ways to reach the client span inside the `execute { }` block under the agent.

### Ambient context

The agent instruments `HttpStatement.execute` so the client span is the ambient OpenTelemetry `Context`
inside the block. This needs only `opentelemetry-api` (plus `opentelemetry-extension-kotlin` for the
coroutine form below) — no instrumentation library dependency. In coroutine code, read it from the
current coroutine context:

```kotlin
import io.opentelemetry.extension.kotlin.getOpenTelemetryContext

client.prepareGet("https://example.com").execute { response ->
  Span.fromContext(currentCoroutineContext().getOpenTelemetryContext())
    .setAttribute("example.attribute", "value")
  // ...
}
```

The ambient form is unique to the agent — it is unavailable with the library instrumentation.

### Library helper

If you also depend on the `opentelemetry-ktor-3.0` library artifact (for example so the same code
runs with either the agent or the library), you can use `HttpClientCall.withClientSpan { }`
instead. The agent bridges the context it stores on the call, so this works identically under the
agent:

```kotlin
import io.opentelemetry.instrumentation.ktor.v3_0.withClientSpan

client.prepareGet("https://example.com").execute { response ->
  response.call.withClientSpan {
    setAttribute("example.attribute", "value")
  }
  // ...
}
```

This is the same API documented in the
[library README](../library/README.md#setting-attributes-on-the-client-span); it reads the span
straight from the call, with no thread-local or coroutine context dependency. It does require the
library artifact on the classpath, which a pure-agent setup would not otherwise need.
