# Settings for the Logback MDC instrumentation

| System property                                       | Type    | Default | Description                                                        |
|-------------------------------------------------------|---------|---------|--------------------------------------------------------------------|
| `otel.instrumentation.logback-mdc.add-baggage`        | Boolean | `false` | Enable exposing baggage attributes through MDC.                    |
| `otel.instrumentation.common.mdc.resource-attributes` | String  |         | Comma separated list of resource attributes to expose through MDC. |
