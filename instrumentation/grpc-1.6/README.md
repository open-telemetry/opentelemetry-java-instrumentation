# Settings for the gRPC instrumentation

| System property                                             | Type    | Default | Description                                                                                                                                                                                                                                                                                                                                                              |
| ----------------------------------------------------------- | ------- | ------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `otel.instrumentation.grpc.emit-message-events`             | Boolean | `true`  | Determines whether to emit span event for each individual message received and sent.                                                                                                                                                                                                                                                                                     |
| `otel.instrumentation.grpc.experimental-span-attributes`    | Boolean | `false` | Enable the capture of experimental span attributes.                                                                                                                                                                                                                                                                                                                      |
| `otel.instrumentation.grpc.client.request-headers.included` | String  |         | A comma-separated list of ASCII request header key patterns to capture on client spans. Matching is case-insensitive. `?` matches one character and `*` matches zero or more characters.                                                                                                                                                                                 |
| `otel.instrumentation.grpc.client.request-headers.excluded` | String  |         | A comma-separated list of ASCII request header key patterns to exclude from client spans. Excluded patterns take precedence over included patterns. Matching is case-insensitive. `?` matches one character and `*` matches zero or more characters. If included is not configured, all non-excluded ASCII headers are captured, which may expose sensitive information. |
| `otel.instrumentation.grpc.server.request-headers.included` | String  |         | A comma-separated list of ASCII request header key patterns to capture on server spans. Matching is case-insensitive. `?` matches one character and `*` matches zero or more characters.                                                                                                                                                                                 |
| `otel.instrumentation.grpc.server.request-headers.excluded` | String  |         | A comma-separated list of ASCII request header key patterns to exclude from server spans. Excluded patterns take precedence over included patterns. Matching is case-insensitive. `?` matches one character and `*` matches zero or more characters. If included is not configured, all non-excluded ASCII headers are captured, which may expose sensitive information. |

Request-header capture is none by default: an absent or empty selector captures no headers.
To select all eligible ASCII request headers explicitly, set
`otel.instrumentation.grpc.client.request-headers.included=*` for client spans or
`otel.instrumentation.grpc.server.request-headers.included=*` for server spans. In declarative
configuration, set `included: ["*"]` under `java.grpc.client.request_headers` or
`java.grpc.server.request_headers`, respectively. Excluded patterns still take precedence.
Omitting `included` captures all non-excluded headers only when non-empty `excluded` patterns
are configured; it does not enable capture by itself. Flat configuration cannot express a
present selector with no patterns in either list. Capturing all headers may expose sensitive information.

Captured headers use `rpc.request.header.<key>`, with lowercase keys and string-array values.
Binary metadata and HTTP/2 pseudo-headers are not captured.
