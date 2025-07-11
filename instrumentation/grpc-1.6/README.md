# Settings for the gRPC instrumentation

| System property                                             | Type    | Default | Description                                                                                                                                                    |
|-------------------------------------------------------------|---------|---------|----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `otel.instrumentation.grpc.emit-message-events`             | Boolean | `true`  | Determines whether to emit span event for each individual message received and sent.                                                                           |
| `otel.instrumentation.grpc.experimental-span-attributes`    | Boolean | `false` | Enable the capture of experimental span attributes.                                                                                                            |
| `otel.instrumentation.grpc.capture-metadata.client.request` | String  |         | A comma-separated list of request metadata keys. gRPC client instrumentation will capture metadata values corresponding to configured keys as span attributes. |
| `otel.instrumentation.grpc.capture-metadata.server.request` | String  |         | A comma-separated list of request metadata keys. gRPC server instrumentation will capture metadata values corresponding to configured keys as span attributes. |
