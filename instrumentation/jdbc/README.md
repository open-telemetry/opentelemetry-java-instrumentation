# Settings for the JDBC instrumentation

| System property                                                   | Type    | Default | Description                                                                                                                                                                                                                                                                   |
|-------------------------------------------------------------------|---------|---------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `otel.instrumentation.jdbc.statement-sanitizer.enabled`           | Boolean | `true`  | Enables the DB statement sanitization.                                                                                                                                                                                                                                        |
| `otel.instrumentation.jdbc.experimental.capture-query-parameters` | Boolean | `false` | Enable the capture of query parameters as span attributes. Enabling this option disables the statement sanitization. <p>WARNING: captured query parameters may contain sensitive information such as passwords, personally identifiable information or protected health info. |
| `otel.instrumentation.jdbc.experimental.transaction.enabled`      | Boolean | `false` | Enables experimental instrumentation to create spans for COMMIT and ROLLBACK operations.                                                                                                                                                                                      |

## Connection Pool Unwrapping

The JDBC instrumentation unwraps pooled connections to cache database metadata efficiently. Most
connection pools support this by default.

**Performance issue?** If unwrapping fails, database metadata is extracted on every operation
instead of being cached, causing higher CPU usage. To fix, ensure your connection pool supports
unwrapping:

**Vibur DBCP example:**
```java
ds.setAllowUnwrapping(true);
```

**Custom wrappers:** Implement `java.sql.Wrapper` correctly to delegate `isWrapperFor()` and
`unwrap()` to the underlying connection.

**Failover note:** Cached metadata uses the unwrapped connection object as the key. If your pool
reuses the same connection object after failover, telemetry may show stale host attributes.
