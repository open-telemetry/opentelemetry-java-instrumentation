## Datadog Java Tracer examples

The goal of this repository is to offer you some examples about how to instrument your code
using the OpenTracing API and the DD Tracer.

![](https://datadog-live.imgix.net/img/datadog_logo_share_tt.png)

Here are the examples
* [Dropwizard (Jax-Rs) + Mongo database + HTTP Client](dropwizard-mongo-client/README.md)
* [Spring-boot + MySQL JDBC database](spring-boot-jdbc/README.md)
* [Instrumenting using a Java Agent](javaagent/README.md)


## Prerequisites

In order to run the demos, you have to do something before:

* Get the latest lib of the DD-Tracer and push it to the lib directory
* Make sure you have a running Datadog Agent on the local port 8126 (default one)
* In the Datadog agent configuration, set APM to true (and restart it)
* Maven
