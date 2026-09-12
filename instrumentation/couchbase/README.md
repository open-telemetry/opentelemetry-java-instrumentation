# Settings for the Couchbase instrumentation

| System property                                               | Type    | Default | Description                                                                                                                                                                  |
| ------------------------------------------------------------- | ------- | ------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `otel.instrumentation.couchbase.emit-experimental-telemetry`  | Boolean | `false` | Under v3 preview, enables the `request_encoding` and `dispatch_to_server` internal spans and experimental span attributes emitted from the underlying Couchbase 3.x library. |
| `otel.instrumentation.couchbase.experimental-span-attributes` | Boolean | `false` | Enables experimental span attributes emitted from the underlying Couchbase library. This setting is ignored for Couchbase 3.x when v3 preview is enabled.                    |
