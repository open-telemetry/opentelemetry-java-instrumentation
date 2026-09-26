# Resilience4j CircuitBreaker Instrumentation

The Resilience4j CircuitBreaker instrumentation for versions 2.0 and higher
emits one `INTERNAL` span for each protected-call attempt made through
Resilience4j's Java decorators while there is an active parent span. This
includes synchronous decorators, `CompletionStage`, and `Future`.
Direct `acquirePermission()` / `tryAcquirePermission()` and completion callbacks
do not create spans; Reactor and RxJava operators use separate lifecycles and
are not covered. The span name is `CircuitBreaker <name>`. Rejected and failed
decorated calls are reported as error spans.

This instrumentation is disabled by default. Enable it by setting
`otel.instrumentation.resilience4j-circuitbreaker.enabled=true`.

## Settings

| System property | Type | Default | Description |
| --- | --- | --- | --- |
| `otel.instrumentation.resilience4j-circuitbreaker.enabled` | Boolean | `false` | Enables the Resilience4j CircuitBreaker instrumentation. |
| `otel.instrumentation.resilience4j-circuitbreaker.experimental-span-attributes` | Boolean | `false` | Enable the capture of experimental span attributes on Resilience4j CircuitBreaker protected-call spans. |

When experimental span attributes are enabled, each CircuitBreaker span records:

- `resilience4j.circuit_breaker.name`
- `resilience4j.circuit_breaker.state`: Resilience4j `CircuitBreaker.State` lowercased, for
  example `closed`, `open`, `half_open`, `disabled`, `forced_open`, or `metrics_only`
- `resilience4j.circuit_breaker.outcome`: `success`, `failure`, `rejected`, or `cancelled`
