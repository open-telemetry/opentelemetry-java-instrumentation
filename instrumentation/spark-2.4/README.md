# Spark Instrumentation

This instrumentation is for
[Spark](https://github.com/perwendel/spark) -
a tiny web framework for Java. It is also sometimes referred to as
"Spark Java" to differentiate it from Apache Spark.

This instrumentation is NOT for Apache Spark.

This instrumentation does not emit telemetry on its own. Instead, it extracts the HTTP route and attaches it to SERVER spans and HTTP server metrics.
