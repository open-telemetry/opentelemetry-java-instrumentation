Database telemetry now uses schema version 1.44.0.
Update telemetry queries and dashboards for `db.system.name`, `db.namespace`, `db.query.text`,
`db.operation.name`, and `db.collection.name` instead of their legacy keys, and for stable system
values such as `microsoft.sql_server`, `oracle.db`, and `h2database`. Database span names use query
summaries or stable operation/target fallbacks rather than legacy database-prefixed names.
Instrumentations using the shared database client metrics now emit `db.client.operation.duration`
in seconds by default. Pool metrics use `db.client.connection.*` rather than
`db.client.connections.*`, including `count` instead of `usage` and `limit` instead of `max`,
singular count units such as `{connection}`, and seconds instead of milliseconds for durations.
Pool attributes use `db.client.connection.pool.name` and `db.client.connection.state`; unnamed
pools use stable database-derived names, and DBCP retains the first registered pool name.
