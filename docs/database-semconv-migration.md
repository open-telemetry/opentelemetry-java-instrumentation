# Database semantic convention migration

Database telemetry uses schema URL `https://opentelemetry.io/schemas/1.44.0`.

## Spans and operation metrics

Update queries, dashboards, and alerts for the stable attributes:

| Legacy attribute | Stable attribute |
| --- | --- |
| `db.system` | `db.system.name` |
| `db.name` | `db.namespace` |
| `db.statement` | `db.query.text` |
| `db.operation` | `db.operation.name` |
| `db.sql.table`, `db.cassandra.table` | `db.collection.name` |

Stable telemetry omits `db.user` and `db.connection_string`. It also records query summaries,
batch-operation attributes, and `error.type` where the instrumentation supports them.
Database system values follow the existing stable mapping, for example `mssql` becomes
`microsoft.sql_server`, `oracle` becomes `oracle.db`, `h2` becomes `h2database`, `db2` becomes `ibm.db2`,
and `cosmosdb` becomes `azure.cosmosdb`.

Span names use the query summary when available, such as `SELECT users`, or the stable operation and
target fallback. The target is the collection, stored procedure, namespace, or configured server
endpoint. Without an operation or target, the database system name is the final fallback.
SQL batch summaries can include a `BATCH` prefix.

Instrumentations using the shared database client operation metrics now record
`db.client.operation.duration` in seconds by default, with stable database attributes.
Instrumentation scope names do not change.

## Connection pool metrics

Update pool metric names and units:

| Legacy metric | Stable metric | Stable unit |
| --- | --- | --- |
| `db.client.connections.usage` | `db.client.connection.count` | `{connection}` |
| `db.client.connections.idle.min` | `db.client.connection.idle.min` | `{connection}` |
| `db.client.connections.idle.max` | `db.client.connection.idle.max` | `{connection}` |
| `db.client.connections.max` | `db.client.connection.limit` | `{connection}` |
| `db.client.connections.pending_requests` | `db.client.connection.pending_requests` | `{request}` |
| `db.client.connections.timeouts` | `db.client.connection.timeouts` | `{timeout}` |
| `db.client.connections.create_time` | `db.client.connection.create_time` | `s` |
| `db.client.connections.wait_time` | `db.client.connection.wait_time` | `s` |
| `db.client.connections.use_time` | `db.client.connection.use_time` | `s` |

Duration values change from milliseconds to seconds. Count units use the singular form.
Pool attributes change from `pool.name` to `db.client.connection.pool.name` and from `state` to
`db.client.connection.state`. Stable pool telemetry also includes available database attributes.

When a pool has no explicit name, instrumentations that derive it from database connection
information use the first available namespace, configured server endpoint, or database system name,
then their instrumentation-specific fallback. DBCP keeps the name selected at first metric
registration until the pool closes, even if later MBean registration provides a JMX name.
