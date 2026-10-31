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
`Flow`, is disabled by default because of a known bug that can resume application code on the wrong
thread and break thread-bound state such as Spring transactions, causing application failures
([#20744](https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/20744)).
Enabling it opts into this unresolved risk, and it requires kotlinx-coroutines 1.4.3 or later:

```properties
otel.instrumentation.kotlinx-coroutines-annotations.enabled=true
```
