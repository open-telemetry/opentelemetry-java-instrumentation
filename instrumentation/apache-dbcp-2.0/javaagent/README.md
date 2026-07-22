# Auto-instrumentation for Apache DBCP

Provides OpenTelemetry auto-instrumentation
for [Apache DBCP](https://commons.apache.org/proper/commons-dbcp/).

This auto-instrumentation registers metrics after `BasicDataSource` completes
`startPoolMaintenance()`, when the connection pool has been initialized, and unregisters metrics
when `BasicDataSource` is closed. JMX registration is not required. When a JMX `ObjectName` is
available, its `name` property is used as the pool name; if the property is absent, the full
`ObjectName` is used. Otherwise, the JDBC URL and connection properties are used to derive
`server.address[:server.port][/db.namespace]`. If neither server address nor database namespace is
available, `apache-dbcp2` is used.
