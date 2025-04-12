# Settings for the OpenTelemetry Instrumentation Annotations integration

Instruments methods annotated with Opentelemetry instrumentation annotations, such as @WithSpan and
@SpanAttribute.

| Environment variable                                                             | Type   | Default | Description                                                                       |
| -------------------------------------------------------------------------------- | ------ | ------- | --------------------------------------------------------------------------------- |
| `otel.instrumentation.opentelemetry-instrumentation-annotations.exclude-methods` | String |         | All methods to be excluded from auto-instrumentation by annotation-based advices. |
