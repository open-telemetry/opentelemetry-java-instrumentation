# Settings for the Apache RocketMQ client instrumentation

| System property | Type | Default | Description |
|---|---|---|---|
| `otel.instrumentation.rocketmq-client.experimental-span-attributes` | Boolean | `false` | Enable the capture of experimental span attributes. |
| `otel.instrumentation.rocketmq-client.propagation` | Boolean | `true` | Enables remote context propagation via RocketMQ message headers. |