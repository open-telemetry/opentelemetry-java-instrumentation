# Settings for the Log4j MDC instrumentation

| System property                                       | Type    | Default       | Description                                                            |
| ----------------------------------------------------- | ------- | ------------- | ---------------------------------------------------------------------- |
| `otel.instrumentation.log4j-context-data.add-baggage` | Boolean | `false`       | Enable exposing baggage entries through MDC, prefixed with `baggage.`. |
| `otel.instrumentation.common.mdc.resource-attributes` | String  |               | Comma separated list of resource attributes to expose through MDC.     |
| `otel.instrumentation.common.logging.trace-id-key`    | String  | `trace_id`    | Customize MDC key name for the trace id.                               |
| `otel.instrumentation.common.logging.span-id-key`     | String  | `span_id`     | Customize MDC key name for the span id.                                |
| `otel.instrumentation.common.logging.trace-flags-key` | String  | `trace_flags` | Customize MDC key name for the trace flags.                            |

## Baggage entries

Baggage entries are exposed with a `baggage.` prefix, to avoid clashes with existing context data
keys. A baggage entry named `clientId` is therefore available as `baggage.clientId`, for example
`%X{baggage.clientId}` in a Log4j pattern.

## How these values are exposed

The values above are supplied through a Log4j `ContextDataProvider`, which is called when a log
event is created. They are not written into `ThreadContext`, so while log patterns and appenders can
read them, `ThreadContext.get("baggage.clientId")` from application code returns `null`.

## Baggage propagation

This instrumentation only reads baggage; it has nothing to do with context propagation, and it never
adds entries to baggage. To propagate values between services you need to put them into baggage
yourself, see the [Baggage API](https://opentelemetry.io/docs/languages/java/api/#baggage) docs.
