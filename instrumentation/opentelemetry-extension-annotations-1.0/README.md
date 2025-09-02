# Settings for the OpenTelemetry Extension Annotations integration

Instruments methods annotated with OpenTelemetry extension annotations, such as @WithSpan and
@SpanAttribute.

| Environment variable                                             | Type   | Default | Description                                                                       |
| ---------------------------------------------------------------- | ------ | ------- | --------------------------------------------------------------------------------- |
| `otel.instrumentation.opentelemetry-annotations.exclude-methods` | String |         | All methods to be excluded from auto-instrumentation by annotation-based advices. |
