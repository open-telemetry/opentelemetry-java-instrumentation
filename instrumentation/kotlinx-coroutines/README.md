# Kotlin Coroutines

Kotlin coroutine library instrumentation is located at
<https://github.com/open-telemetry/opentelemetry-java/tree/main/extensions/kotlin>

Instrumentation of Kotlin suspend functions annotated with `@WithSpan` is disabled by default and
can be enabled with `otel.instrumentation.kotlinx-coroutines-annotations.enabled=true`. Ordinary
coroutine context propagation remains enabled; the `kotlinx-coroutines` selector does not enable
suspend function annotations. `@WithSpan` methods returning `Flow` are still instrumented by the
default-enabled annotations instrumentation.
