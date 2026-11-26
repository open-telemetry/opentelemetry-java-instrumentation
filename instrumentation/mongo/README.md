# Settings for the MongoDB Java Driver instrumentation

| System property                                         | Type    | Default   | Description                                                                                                                            |
| ------------------------------------------------------- | ------- | --------- | -------------------------------------------------------------------------------------------------------------------------------------- |
| `otel.instrumentation.mongo.capture-query-text`         | Boolean | `false`   | Captures query text in the `db.query.text` span attribute.                                                                             |
| `otel.instrumentation.mongo.query-sanitization.enabled` | Boolean | Inherited | Masks query values when capture is enabled. Defaults to `otel.instrumentation.common.db.query-sanitization.enabled` (normally `true`). |
