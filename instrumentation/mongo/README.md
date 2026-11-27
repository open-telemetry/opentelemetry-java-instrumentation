# Settings for the MongoDB Java Driver instrumentation

| System property                                         | Type    | Default | Description                                                                                                                      |
| ------------------------------------------------------- | ------- | ------- | -------------------------------------------------------------------------------------------------------------------------------- |
| `otel.instrumentation.mongo.capture-query-text`         | Boolean | `false` | Enables capturing MongoDB query text in the `db.query.text` span attribute.                                                      |
| `otel.instrumentation.mongo.query-sanitization.enabled` | Boolean | `true`  | Enables query sanitization for MongoDB queries. When unset, `otel.instrumentation.common.db.query-sanitization.enabled` applies. |
