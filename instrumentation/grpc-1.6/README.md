# Settings for the gRPC instrumentation

| System property                                             | Type    | Default | Description                                                                                                                                                                                                                |
| ----------------------------------------------------------- | ------- | ------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `otel.instrumentation.grpc.emit-message-events`              | Boolean | `true`  | Determines whether to emit span event for each individual message received and sent.                                                                                                                                         |
| `otel.instrumentation.grpc.experimental-span-attributes`     | Boolean | `false` | Enable the capture of experimental span attributes.                                                                                                                                                                         |
| `otel.instrumentation.grpc.client.request-captured-headers`   | String  |         | A comma-separated list of ASCII request header names to capture on client spans. Matching is case-insensitive and exact; wildcards are not supported. No headers are captured when unset or empty.                           |
| `otel.instrumentation.grpc.server.request-captured-headers`   | String  |         | A comma-separated list of ASCII request header names to capture on server spans. Matching is case-insensitive and exact; wildcards are not supported. No headers are captured when unset or empty.                           |

Captured headers use `rpc.request.header.<key>`, with lowercase keys and string-array values.
Binary metadata and HTTP/2 pseudo-headers are not captured.
