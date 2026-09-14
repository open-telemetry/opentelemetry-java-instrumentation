# Settings for the Couchbase instrumentation

| System property                                               | Type    | Default | Description                                                                                                                                                                                      |
| ------------------------------------------------------------- | ------- | ------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `otel.instrumentation.couchbase.emit-experimental-telemetry`  | Boolean | `false` | Enables experimental span attributes emitted from the underlying Couchbase 3.x library. When v3 preview is enabled, also enables the `request_encoding` and `dispatch_to_server` internal spans. |
| `otel.instrumentation.couchbase.experimental-span-attributes` | Boolean | `false` | Deprecated for Couchbase 3.x and will be removed in the next minor release. Use `otel.instrumentation.couchbase.emit-experimental-telemetry` instead.                                            |
