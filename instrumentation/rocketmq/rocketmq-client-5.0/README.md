# Settings for the Apache RocketMQ gRPC client instrumentation

| System property | Type | Default | Description |
| --- | --- | --- | --- |
| `otel.instrumentation.rocketmq-client.experimental-span-attributes` | Boolean | `false` | Capture `rocketmq.message.lite_topic` and `rocketmq.message.priority` on messaging spans and receive span links when the client provides those values. |
