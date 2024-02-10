# Settings for the Kafka instrumentation

| System property                                           | Type    | Default | Description                                                                                                                    |
|-----------------------------------------------------------| ------- |---------|--------------------------------------------------------------------------------------------------------------------------------|
| `otel.instrumentation.kafka.experimental-span-attributes` | Boolean | `false` | Enable the capture of experimental span attributes.                                                                            |
| `otel.instrumentation.kafka.producer-propagation.enabled` | Boolean | `true`  | Enable context propagation for kafka message producer.                                                                         |
| `otel.instrumentation.kafka.metric-reporter.enabled`      | Boolean | `true`  | Enable kafka consumer and producer metrics. **Deprecated**, disable instrumentation with name `kafka-clients-metrics` instead. |
