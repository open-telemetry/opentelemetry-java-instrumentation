# Kotlin Coroutines

Kotlin coroutine library instrumentation is located at
<https://github.com/open-telemetry/opentelemetry-java/tree/main/extensions/kotlin>

Coroutine `@WithSpan` annotation instrumentation is disabled by default and can be enabled with
`otel.instrumentation.kotlinx-coroutines-annotations.enabled=true`. Ordinary coroutine context
propagation remains enabled; the `kotlinx-coroutines` selector does not enable annotations. For
declarative distribution configuration, add `kotlinx_coroutines_annotations` to
`distribution.javaagent.instrumentation.enabled`.
