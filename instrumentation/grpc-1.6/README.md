# Settings for the gRPC instrumentation

| System property                                             | Type    | Default | Description                                                                               |
|-------------------------------------------------------------|---------|---------|-------------------------------------------------------------------------------------------|
| `otel.instrumentation.grpc.experimental-span-attributes`    | Boolean | `false` | Enable the capture of experimental span attributes.                                       |
| `otel.instrumentation.grpc.capture-metadata.client.request` | String  |         | Comma separated list of request metadata keys that should be captured as span attributes. |
| `otel.instrumentation.grpc.capture-metadata.server.request` | String  |         | Comma separated list of request metadata keys that should be captured as span attributes. |
