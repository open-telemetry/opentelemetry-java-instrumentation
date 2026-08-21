# Settings for the Resilience4j instrumentation

The Resilience4j CircuitBreaker instrumentation emits one `INTERNAL` span for each protected-call
attempt while there is an active parent span. The span name is `CircuitBreaker <name>`. Rejected and
failed calls are reported as error spans.

| System property | Type | Default | Description |
| --- | --- | --- | --- |
| `otel.instrumentation.resilience4j-circuitbreaker.experimental-span-attributes` | Boolean | `false` | Enable the capture of experimental span attributes on Resilience4j CircuitBreaker protected-call spans. |

When experimental span attributes are enabled, each CircuitBreaker span records:

- `resilience.policy.name`
- `resilience.circuit_breaker.state`: Resilience4j `CircuitBreaker.State` lowercased, for
  example `closed`, `open`, `half_open`, `disabled`, `forced_open`, or `metrics_only`
- `resilience.circuit_breaker.outcome`: `success`, `failure`, or `rejected`
