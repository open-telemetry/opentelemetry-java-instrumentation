# Settings for the JDBC instrumentation

| System property                                         | Type    | Default | Description                                        |
|---------------------------------------------------------|---------|---------|----------------------------------------------------|
| `otel.instrumentation.jdbc.statement-sanitizer.enabled` | Boolean | `true`  | Enables the DB statement sanitization.             |
| `otel.instrumentation.jdbc.operation-parameter.enabled` | Boolean | `false` | Enables the attribute db.operation.parameter.<key> |
