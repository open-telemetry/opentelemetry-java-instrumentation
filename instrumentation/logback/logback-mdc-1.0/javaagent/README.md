# Settings for the Logback MDC instrumentation

| System property                                       | Type    | Default       | Description                                                            |
| ----------------------------------------------------- | ------- | ------------- | ---------------------------------------------------------------------- |
| `otel.instrumentation.logback-mdc.add-baggage`        | Boolean | `false`       | Enable exposing baggage entries through MDC, prefixed with `baggage.`. |
| `otel.instrumentation.common.mdc.resource-attributes` | String  |               | Comma separated list of resource attributes to expose through MDC.     |
| `otel.instrumentation.common.logging.trace-id-key`    | String  | `trace_id`    | Customize MDC key name for the trace id.                               |
| `otel.instrumentation.common.logging.span-id-key`     | String  | `span_id`     | Customize MDC key name for the span id.                                |
| `otel.instrumentation.common.logging.trace-flags-key` | String  | `trace_flags` | Customize MDC key name for the trace flags.                            |

## Baggage entries

Baggage entries are exposed with a `baggage.` prefix, to avoid clashes with existing MDC keys. A
baggage entry named `clientId` is therefore available as `baggage.clientId`, for example
`%mdc{baggage.clientId}` in a Logback pattern.

## How these values are exposed

The values above are added to the MDC property map that Logback passes to appenders and encoders.
They are not written into the SLF4J `MDC` thread local, so while log patterns and appenders can read
them, `MDC.get("baggage.clientId")` from application code returns `null`.

## Baggage propagation

This instrumentation only reads baggage; it has nothing to do with context propagation, and it never
adds entries to baggage. To propagate values between services you need to put them into baggage
yourself, see the [Baggage API](https://opentelemetry.io/docs/languages/java/api/#baggage) docs.
