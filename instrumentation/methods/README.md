# Settings for the methods instrumentation

Provides a flexible way to capture telemetry at the method level in JVM applications. By weaving
instrumentation into targeted methods at runtime based on the "otel.instrumentation.methods.include"
configuration property, it measures entry and exit points, execution duration and exception
occurrences. The resulting data is automatically translated into OpenTelemetry traces.

| System property                        | Type   | Default | Description                                                                                                                                        |
| -------------------------------------- | ------ | ------- | -------------------------------------------------------------------------------------------------------------------------------------------------- |
| `otel.instrumentation.methods.include` | String | None    | List of methods to include for tracing. For more information, see [Creating spans around methods with `otel.instrumentation.methods.include`][cs]. |

[cs]: https://opentelemetry.io/docs/zero-code/java/agent/annotations/#creating-spans-around-methods-with-otelinstrumentationmethodsinclude
