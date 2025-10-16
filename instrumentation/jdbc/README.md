# Settings for the JDBC instrumentation

| System property                                                   | Type    | Default | Description                                                                                                                                                                                                                                                                                                                                  |
|-------------------------------------------------------------------|---------|---------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `otel.instrumentation.jdbc.statement-sanitizer.enabled`           | Boolean | `true`  | Enables the DB statement sanitization.                                                                                                                                                                                                                                                                                                       |
| `otel.instrumentation.jdbc.experimental.capture-query-parameters` | Boolean | `false` | Enable the capture of query parameters as span attributes. Enabling this option disables the statement sanitization. <p>WARNING: captured query parameters may contain sensitive information such as passwords, personally identifiable information or protected health info.                                                                |
| `otel.instrumentation.jdbc.experimental.transaction.enabled`      | Boolean | `false` | Enables experimental instrumentation to create spans for COMMIT and ROLLBACK operations.                                                                                                                                                                                                                                                     |
| `otel.instrumentation.jdbc.experimental.sqlcommenter.enabled`     | Boolean | `false` | Enables augmenting queries with a comment containing the tracing information. See [sqlcommenter](https://google.github.io/sqlcommenter/) for more info. WARNING: augmenting queries with tracing context will make query texts unique, which may have adverse impact on database performance. Consult with database experts before enabling. |

## Connection Pool Unwrapping

The JDBC instrumentation requires unwrapping pooled connections (via `java.sql.Wrapper`) to
correctly attribute database operations to the underlying connection and to cache metadata. Most
connection pools support this by default.

**Performance issue?** If unwrapping fails, the instrumentation may have degraded performance or
increased overhead with some JDBC drivers. Metadata extraction may result in database queries on
every operation (depending on driver implementation and caching behavior) instead of being cached,
and operations may be attributed to the wrong database connection. To fix, ensure your connection
pool supports unwrapping:

**Vibur DBCP example:**
```java
ds.setAllowUnwrapping(true);
```

**Custom wrappers:** Implement `java.sql.Wrapper` correctly to delegate `isWrapperFor()` and
`unwrap()` to the underlying connection.

**Failover note:** Cached metadata uses the unwrapped connection object as the key. If your pool
reuses the same connection object after failover, telemetry may show stale host attributes.
