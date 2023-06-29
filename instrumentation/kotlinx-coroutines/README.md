# Kotlin Coroutines

[opentelemetry-java](https://github.com/open-telemetry/opentelemetry-java) has various plugins mentioned in their [README](https://github.com/open-telemetry/opentelemetry-java/tree/main#api-extensions)

To enable coroutine support, you need to add the `io.opentelemetry:opentelemetry-extension-kotlin` dependency.

Then you can do:

```kotlin
launch(Context.current().asContextElement()) { 
// trace ids show up
}
```

Kotlin coroutine library instrumentation is located at
[OpenTelemetry Java Kotlin Extension](https://github.com/open-telemetry/opentelemetry-java/tree/main/extensions/kotlin)
