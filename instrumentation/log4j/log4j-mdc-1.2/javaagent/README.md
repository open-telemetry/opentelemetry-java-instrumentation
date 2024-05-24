# Settings for the Log4j MDC instrumentation

| System property                                       | Type    | Default       | Description                                                        |
|-------------------------------------------------------|---------|---------------|--------------------------------------------------------------------|
| `otel.instrumentation.common.mdc.resource-attributes` | String  |               | Comma separated list of resource attributes to expose through MDC. |
| `otel.instrumentation.common.logging.trace-id`        | String  | `trace_id`    | Customize the key name of the trace_id in MDC.                     |
| `otel.instrumentation.common.logging.span-id`         | String  | `span_id`     | Customize the key name of the span_id in MDC.                      |
| `otel.instrumentation.common.logging.trace-flags`     | String  | `trace_flags` | Customize the key name of the trace_flags in MDC.                  |
