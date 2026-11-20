# Kotlin Coroutines

Kotlin coroutine library instrumentation is located at
<https://github.com/open-telemetry/opentelemetry-java/tree/main/extensions/kotlin>

Coroutine context propagation is enabled by default, including across suspension and dispatcher
switches, and uses the `kotlinx-coroutines` selector.

## `@WithSpan`

Ordinary non-suspend Kotlin functions annotated with `@WithSpan` are instrumented by default
through the `opentelemetry-instrumentation-annotations` selector. This includes functions returning
`Flow`; their spans end when collection completes.

Instrumentation of functions declared with `suspend`, including suspend functions returning
`Flow`, is disabled by default. It requires kotlinx-coroutines 1.4.3 or later. A known issue is that
an annotated suspend function that tail calls `emit` inside a `flow {}` builder fails collection
([#21159](https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/21159)).
To enable it:

```properties
otel.instrumentation.kotlinx-coroutines-annotations.enabled=true
```
