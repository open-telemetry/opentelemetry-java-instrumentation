# Auto-instrumentation for Tomcat DBCP

Provides OpenTelemetry auto-instrumentation for
[Tomcat DBCP](https://tomcat.apache.org/tomcat-8.5-doc/api/org/apache/tomcat/dbcp/dbcp2/BasicDataSource.html).

This auto-instrumentation registers metrics when the connection pool is initialized
(`BasicDataSource.startPoolMaintenance()`) or when `BasicDataSource.preRegister(...)` is invoked
during MBean registration, whichever comes first, and unregisters them when the `BasicDataSource`
is closed. JMX registration is not required. When a JMX `ObjectName` is available, its `name`
property is used as the pool name; if the property is absent, the full `ObjectName` is used.
Otherwise, the JDBC URL and connection properties are used to derive
`server.address[:server.port][/db.namespace]`. If neither server address nor database namespace is
available, `tomcat-dbcp` is used. A pool registered as an MBean after it has already started
switches from the derived name to the `ObjectName`-based name.
