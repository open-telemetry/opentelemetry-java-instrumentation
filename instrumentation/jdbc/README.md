# Settings for the JDBC instrumentation

| System property                                                   | Type    | Default | Description                                                                                                                                                                                                                                                                                                                                  |
|-------------------------------------------------------------------|---------|---------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `otel.instrumentation.jdbc.statement-sanitizer.enabled`           | Boolean | `true`  | Enables the DB statement sanitization.                                                                                                                                                                                                                                                                                                       |
| `otel.instrumentation.jdbc.experimental.capture-query-parameters` | Boolean | `false` | Enable the capture of query parameters as span attributes. Enabling this option disables the statement sanitization. <p>WARNING: captured query parameters may contain sensitive information such as passwords, personally identifiable information or protected health info.                                                                |
| `otel.instrumentation.jdbc.experimental.transaction.enabled`      | Boolean | `false` | Enables experimental instrumentation to create spans for COMMIT and ROLLBACK operations.                                                                                                                                                                                                                                                     |
| `otel.instrumentation.jdbc.experimental.sqlcommenter.enabled`     | Boolean | `false` | Enables augmenting queries with a comment containing the tracing information. See [sqlcommenter](https://google.github.io/sqlcommenter/) for more info. WARNING: augmenting queries with tracing context will make query texts unique, which may have adverse impact on database performance. Consult with database experts before enabling. |

## Connection Pool Unwrapping

The instrumentation unwraps pooled connections (via java.sql.Wrapper) to cache database metadata
against the underlying physical connection. Most connection pools support this by default.

In the case that a connection pool doesn't support this, caching is limited
to the wrapper instance, which typically changes each time a connection is retrieved from the pool.
This can result in repeated metadata extraction, potentially causing performance degradation.
