# Settings for the external annotations instrumentation

The external-annotations instrumentation acts as a "shim" that automatically instruments methods
annotated with custom or third-party tracing annotations. This is particularly useful if you have
existing annotations (such as a custom @Trace or third-party annotation) that you want to leverage
with OpenTelemetry. At runtime, this module recognizes those annotations and applies the appropriate
OpenTelemetry instrumentation logic, including span creation and context propagation. Covers many
common vendor annotations by default, and additional annotations can be targeted using the
configuration property "otel.instrumentation.external-annotations.include".

| System property                                             | Type   | Default             | Description                                                                                               |
| ----------------------------------------------------------- | ------ | ------------------- | --------------------------------------------------------------------------------------------------------- |
| `otel.instrumentation.external-annotations.include`         | String | Default annotations | Configuration for trace annotations, in the form of a pattern that matches `'package.Annotation$Name;*'`. |
| `otel.instrumentation.external-annotations.exclude-methods` | String |                     | All methods to be excluded from auto-instrumentation by annotation-based advices.                         |
