# Settings for the Kafka instrumentation

| System property                                           | Type    | Default | Description                                            |
| --------------------------------------------------------- | ------- | ------- | ------------------------------------------------------ |
| `otel.instrumentation.kafka-clients-metrics.enabled`     | Boolean | `false` | Enable native Kafka client metrics.                   |
| `otel.instrumentation.kafka.experimental-span-attributes` | Boolean | `false` | Enable the capture of experimental span attributes.    |
| `otel.instrumentation.kafka.producer-propagation.enabled` | Boolean | `true`  | Enable context propagation for kafka message producer. |

Kafka tracing and messaging operation metrics remain enabled independently of native Kafka client
metrics; the `kafka` and `kafka-clients` selectors do not enable native metrics. To enable native
client metrics with declarative distribution configuration, add `kafka_clients_metrics` to
`distribution.javaagent.instrumentation.enabled`.
