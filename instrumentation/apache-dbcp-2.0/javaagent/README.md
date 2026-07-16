# Auto-instrumentation for Apache DBCP

Provides OpenTelemetry auto-instrumentation
for [Apache DBCP](https://commons.apache.org/proper/commons-dbcp/).

This auto-instrumentation detects the `BasicDataSource` lifecycle when the underlying `DataSource`
is created and closed. JMX registration is not required. When no data source name is available
from JMX registration, the JDBC URL and connection properties are used to derive
`server.address[:server.port][/db.namespace]`. If no server address or database namespace is
available, `apache-dbcp2` is used.
