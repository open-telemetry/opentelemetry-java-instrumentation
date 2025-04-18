# Settings for the JDBC instrumentation

| System property                                         | Type    | Default | Description                                                                                                                                             |
|---------------------------------------------------------|---------|---------|---------------------------------------------------------------------------------------------------------------------------------------------------------|
| `otel.instrumentation.jdbc.statement-sanitizer.enabled` | Boolean | `true`  | Enables the DB statement sanitization.                                                                                                                  |
| `otel.instrumentation.jdbc.sqlcommenter.enabled`        | Boolean | `false` | Enables augmenting queries with a comment containing the tracing information. See [sqlcommenter](https://google.github.io/sqlcommenter/) for more info. |
