Reactor now propagates non-span OpenTelemetry `Context` values through externally driven callbacks instead of treating a missing valid span as an empty context. This keeps baggage and instrumentation context available to callback-driven publishers such as messaging consumers.

```java
Context context = Context.root().with(key, value);
try (Scope ignored = context.makeCurrent()) {
  publisher.subscribe(...);
}
```

The callback receives `value` even when `context` has no span. If the captured context explicitly stores `Span.getInvalid()`, Reactor preserves that clear instead of inheriting the span active when the signal arrives.

Related to #19955.
