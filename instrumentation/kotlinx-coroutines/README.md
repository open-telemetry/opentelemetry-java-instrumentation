# Kotlin Coroutines

Kotlin coroutine library instrumentation is located at
<https://github.com/open-telemetry/opentelemetry-java/tree/main/extensions/kotlin>

Instrumentation of Kotlin suspend functions annotated with `@WithSpan` uses the independent
`kotlinx-coroutines-annotations` selector.

To disable suspend function annotations without disabling ordinary coroutine context propagation:

```properties
otel.instrumentation.kotlinx-coroutines-annotations.enabled=false
```

The `kotlinx-coroutines` selector controls ordinary coroutine context propagation, not suspend
function annotations. Non-suspend `@WithSpan` methods, including methods returning `Flow`, remain
controlled by the existing `opentelemetry-instrumentation-annotations` selector.
