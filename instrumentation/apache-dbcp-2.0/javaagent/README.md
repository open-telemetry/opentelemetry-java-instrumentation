# Auto-instrumentation for Apache DBCP

Provides OpenTelemetry auto-instrumentation
for [Apache DBCP](https://commons.apache.org/proper/commons-dbcp/).

This auto-instrumentation registers metrics when the connection pool is initialized
(`BasicDataSource.startPoolMaintenance()`) or when `BasicDataSource.preRegister(...)` is invoked
during MBean registration, whichever comes first, and unregisters them when the `BasicDataSource`
is closed. JMX registration is not required. When a JMX `ObjectName` is available, its `name`
property is used as the pool name; if the property is absent, the full `ObjectName` is used.
Otherwise, the JDBC URL and connection properties are used to derive
the pool name. With legacy database semantic conventions, the derived format is
`server.address[:server.port][/db.namespace]`. With stable database semantic conventions, the first
available value among `db.namespace`, the configured endpoint
(`server.address[:server.port]`), and `db.system.name` is used. If none is available, `apache-dbcp2`
is used. The pool name selected when metrics are first registered is retained until the pool is
closed, including when MBean registration occurs later.
