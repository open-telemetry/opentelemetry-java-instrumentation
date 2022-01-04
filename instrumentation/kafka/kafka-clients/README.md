# Settings for the Kafka client instrumentation

| System property | Type | Default | Description |
|---|---|---|---|
| `otel.instrumentation.kafka.experimental-span-attributes` | Boolean | `false` | Enable the capture of experimental span attributes. |
| `otel.instrumentation.kafka.client-propagation.enabled` | Boolean | `true` | Enables remote context propagation via Kafka message headers. |