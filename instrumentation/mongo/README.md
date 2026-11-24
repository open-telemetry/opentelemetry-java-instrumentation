# Settings for the MongoDB Java Driver instrumentation

| System property                                         | Type    | Default | Description                                                |
| ------------------------------------------------------- | ------- | ------- | ---------------------------------------------------------- |
| `otel.instrumentation.mongo.capture-query-text`         | Boolean | `false` | Captures query text in the `db.query.text` span attribute. |
| `otel.instrumentation.mongo.query-sanitization.enabled` | Boolean | `true`  | Masks query values when query text capture is enabled.     |

When instrumenting a driver directly with `MongoTelemetry`, call
`MongoTelemetry.builder(openTelemetry).setCaptureQueryText(true)` before building the telemetry.
