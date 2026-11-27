# Settings for the Log4j MDC instrumentation

| System property                                          | Type    | Default       | Description                                                                                                                           |
| -------------------------------------------------------- | ------- | ------------- | ------------------------------------------------------------------------------------------------------------------------------------- |
| `otel.instrumentation.logging.pattern-injection.enabled` | Boolean | `false`       | Add missing correlation fields to text patterns. See [automatic text log correlation](../../../../docs/logging-pattern-injection.md). |
| `otel.instrumentation.common.mdc.resource-attributes`    | String  |               | Comma separated list of resource attributes to expose through MDC.                                                                    |
| `otel.instrumentation.common.logging.trace-id-key`       | String  | `trace_id`    | Customize MDC key name for the trace id.                                                                                              |
| `otel.instrumentation.common.logging.span-id-key`        | String  | `span_id`     | Customize MDC key name for the span id.                                                                                               |
| `otel.instrumentation.common.logging.trace-flags-key`    | String  | `trace_flags` | Customize MDC key name for the trace flags.                                                                                           |
