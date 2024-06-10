# Settings for the Log4j MDC instrumentation

| System property                                       | Type    | Default       | Description                                                        |
|-------------------------------------------------------|---------|---------------|--------------------------------------------------------------------|
| `otel.instrumentation.log4j-context-data.add-baggage` | Boolean | `false`       | Enable exposing baggage attributes through MDC.                    |
| `otel.instrumentation.common.mdc.resource-attributes` | String  |               | Comma separated list of resource attributes to expose through MDC. |
| `otel.instrumentation.common.logging.trace-id`        | String  | `trace_id`    | Customize MDC key name for the trace id.                           |
| `otel.instrumentation.common.logging.span-id`         | String  | `span_id`     | Customize MDC key name for the span id.                            |
| `otel.instrumentation.common.logging.trace-flags`     | String  | `trace_flags` | Customize MDC key name for the trace flags.                        |
