# Settings for the JDBC instrumentation

| System property                                          | Type    | Default | Description                                                                                                                                             |
|----------------------------------------------------------|---------|---------|---------------------------------------------------------------------------------------------------------------------------------------------------------|
| `otel.instrumentation.r2dbc.statement-sanitizer.enabled` | Boolean | `true`  | Enables the DB statement sanitization.                                                                                                                  |
| `otel.instrumentation.r2dbc.sqlcommenter.enabled`        | Boolean | `false` | Enables augmenting queries with a comment containing the tracing information. See [sqlcommenter](https://google.github.io/sqlcommenter/) for more info. |
