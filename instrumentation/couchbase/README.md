# Settings for the Couchbase instrumentation

| System property                                              | Type    | Default | Description                                                                                                                                                   |
| ------------------------------------------------------------ | ------- | ------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `otel.instrumentation.couchbase.emit-experimental-telemetry` | Boolean | `false` | Enables experimental span attributes for Couchbase 2.x. For Couchbase 3.x, also enables the SDK's `request_encoding` and `dispatch_to_server` internal spans. |
