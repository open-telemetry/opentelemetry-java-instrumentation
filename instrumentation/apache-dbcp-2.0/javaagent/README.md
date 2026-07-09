# Auto-instrumentation for Apache DBCP

Provides OpenTelemetry auto-instrumentation
for [Apache DBCP](https://commons.apache.org/proper/commons-dbcp/).

This auto-instrumentation detects the `BasicDataSource` lifecycle when the underlying `DataSource`
is created and closed. JMX registration is not required. When no data source name is configured or
available from JMX registration, a generated `dbcp2-N` name is used.
