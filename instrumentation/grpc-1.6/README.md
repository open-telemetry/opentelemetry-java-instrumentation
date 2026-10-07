# Settings for the gRPC instrumentation

| System property                                              | Type    | Default | Description                                                                                                                                                                                                                                                                                                                                                                |
| ------------------------------------------------------------ | ------- | ------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `otel.instrumentation.grpc.emit-message-events`              | Boolean | `true`  | Determines whether to emit span event for each individual message received and sent.                                                                                                                                                                                                                                                                                       |
| `otel.instrumentation.grpc.experimental-span-attributes`     | Boolean | `false` | Enable the capture of experimental span attributes.                                                                                                                                                                                                                                                                                                                        |
| `otel.instrumentation.grpc.client.request-metadata.included` | String  |         | A comma-separated list of ASCII request metadata key patterns to capture on client spans. Matching is case-insensitive. `?` matches one character and `*` matches zero or more characters.                                                                                                                                                                                 |
| `otel.instrumentation.grpc.client.request-metadata.excluded` | String  |         | A comma-separated list of ASCII request metadata key patterns to exclude from client spans. Excluded patterns take precedence over included patterns. Matching is case-insensitive. `?` matches one character and `*` matches zero or more characters. If included is not configured, all non-excluded ASCII metadata is captured, which may expose sensitive information. |
| `otel.instrumentation.grpc.server.request-metadata.included` | String  |         | A comma-separated list of ASCII request metadata key patterns to capture on server spans. Matching is case-insensitive. `?` matches one character and `*` matches zero or more characters.                                                                                                                                                                                 |
| `otel.instrumentation.grpc.server.request-metadata.excluded` | String  |         | A comma-separated list of ASCII request metadata key patterns to exclude from server spans. Excluded patterns take precedence over included patterns. Matching is case-insensitive. `?` matches one character and `*` matches zero or more characters. If included is not configured, all non-excluded ASCII metadata is captured, which may expose sensitive information. |

The deprecated `otel.instrumentation.grpc.capture-metadata.client.request` and
`otel.instrumentation.grpc.capture-metadata.server.request` settings no longer configure metadata
capture. Use the request metadata selectors instead:

```properties
# Before
otel.instrumentation.grpc.capture-metadata.client.request=custom-key
otel.instrumentation.grpc.capture-metadata.server.request=custom-key

# After
otel.instrumentation.grpc.client.request-metadata.included=custom-key
otel.instrumentation.grpc.server.request-metadata.included=custom-key
```
