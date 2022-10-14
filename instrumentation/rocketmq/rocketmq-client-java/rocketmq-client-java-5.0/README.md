# Settings for the Apache RocketMQ gRPC/protobuf-based client instrumentation

| System property | Type | Default | Description |
|---|---|---|---|
| `otel.instrumentation.rocketmq-client-java.propagation` | Boolean | `true` | Enables remote context propagation via RocketMQ message property. |