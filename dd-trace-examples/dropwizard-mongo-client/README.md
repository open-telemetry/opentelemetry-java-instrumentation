## Dropwizard-mongo-client example

### Purpose

This project aims at demonstrating how to instrument legacy code based on Dropwizard and a client querying a Mongo database.
We also demonstrate cross-process tracing through the `TracedClient` example.

### Run the demo

#### Prerequisites
1. Please make sure that you read and executed the prerequisites provided [on this page](../README.md)
2. Make sure that you have a local mongo database running.

#### Run the application

If you want to enable tracing you have to launch the application with the datadog java agent.
Get the latest version of the dd-java-agent: 

```
# use latest version 
curl -OL http://central.maven.org/maven2/com/datadoghq/dd-java-agent/0.0.1/dd-java-agent-0.0.1.jar
```

Then add the agent to the JVM. That can be done by providing the following argument:
`-javaagent:/path/to/dd-java-agent-0.0.1.jar`.

### Generate traces

*A trace example*

![](./apm.png)

#### With your web browser

Once the application runs. Go to the following url:

* [http://localhost:8080/demo/]()

Then get back to Datadog and wait a bit to see a trace coming.

#### Cross process tracing: with the provided `TracedClient` class

Runs the `TracedClient` class with the java agent as explained above.

In that case, we instrument the `OkHttpClient` and you then observe a similar trace as the example just above but with the client as the originating root span.

Cross process tracing is working thanks to headers injected on the client side that are extracted on the server. If you want to understand more you can refer to the [opentracing documentation](http://opentracing.io/documentation/pages/api/cross-process-tracing.html).

### How did we instrument this project?

#### Auto-instrumentation with the `dd-trace-agent`

The instrumentation is entirely done by the datadog agent which embed a set of rules that automatically recognizes & instruments:

- The java servlet filters
- The Mongo client
- The OkHTTP client

The datadog agent embeds the [open tracing java agent](https://github.com/opentracing-contrib/java-agent).

#### Custom methods instrumentation

As an illustration of

We wrote 4 rules in the `otarules.btm` files in order to instrument the `HelloWorldResource.beforeDB()` & `HelloWorldResource.afterDB()` methods.

It brief, it consists in wrapping the content of this method with 2 rules:

* 1 ENTRY rule: that start a child span
* 1 EXIT rule: that finishes & deactivate the current span

We encourage to open the rules file to get the details.
