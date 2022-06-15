# Auto-instrumentation for Apache DBCP

Provides OpenTelemetry auto-instrumentation
for [Apache DBCP](https://commons.apache.org/proper/commons-dbcp/).

This auto-instrumentation uses `MBeanRegistration` methods for lifecycle detection, therefore it
only activates if the `BasicDataSource` is registered to an `MBeanServer`. If using Spring Boot,
this happens automatically as all Spring beans that support JMX registration are automatically
registered by default.
