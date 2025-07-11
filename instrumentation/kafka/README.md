# Settings for the Kafka instrumentation

| System property                                           | Type    | Default | Description                                                                |
|-----------------------------------------------------------|---------|---------|----------------------------------------------------------------------------|
| `otel.instrumentation.kafka.experimental-span-attributes` | Boolean | `false` | Enable the capture of experimental span attributes.                        |
| `otel.instrumentation.kafka.producer-propagation.enabled` | Boolean | `true`  | Enable context propagation for kafka message producer.                     |
| `otel.instrumentation.messaging.experimental.capture-headers` | List    | Empty   | Enable the capture of experimental headers in messaging systems.           |
| `otel.instrumentation.messaging.experimental.receive-telemetry.enabled` | Boolean | `false` | Enable the capture of experimental receive telemetry in messaging systems. |
---------
