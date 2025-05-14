# Settings for the JDBC instrumentation

| System property                                              | Type    | Default | Description                                                                              |
|--------------------------------------------------------------|---------|---------|------------------------------------------------------------------------------------------|
| `otel.instrumentation.jdbc.statement-sanitizer.enabled`      | Boolean | `true`  | Enables the DB statement sanitization.                                                   |
| `otel.instrumentation.jdbc.experimental.transaction.enabled` | Boolean | `false` | Enables experimental instrumentation to create spans for COMMIT and ROLLBACK operations. |
