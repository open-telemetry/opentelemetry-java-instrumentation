## Dropwizard-mongo-client example

### Purpose

This project aims at demonstrating how to instrument legacy code based on:
* the Dropwizard framework
* a Mongo database.

We also demonstrate user cross-process tracing through the `TracedClient` example.

### Run the demo

#### Prerequisites

1. Make sure that you have a local mongo database running (hostname: `localhost`, port: `27017`).
2. No process holding the 8080 and the 8081 port, they are used by the Dropwizard server.

If you're using Docker, you can run a mongo instance as follow:

```bash
docker run -it --rm -p 27017:27017 --name mongo -d mongo
```

#### Run the application

If you want to enable tracing you have to launch the application with the Datadog java agent.
First, get the latest version of the dd-java-agent: 

```
# Use latest version 
curl -OL http://central.maven.org/maven2/com/datadoghq/dd-java-agent/{version}/dd-java-agent-{version}.jar
```


Then, build the app add the agent to the JVM. That can be done as follow:
```
cd path/to/dd-trace-examples/dropwizard-mongo-client
gradle clean shadowJar
java -javaagent:/path/to/dd-java-agent-{version}.jar  -jar  build/libs/dropwizard-mongo-client-{version}-all.jar server
```
### Generate traces


#### With your web browser

Once the application runs. Go to the following url:

* [http://localhost:8080/demo/add?title=some-book-title&isbn=1234&page=42]()
* [http://localhost:8080/demo/]()


Then get back to Datadog and wait a bit to see a trace coming.

#### Cross process tracing: with the provided `TracedClient` class

Runs the `TracedClient` class with the java agent as explained above.

In that case, we instrument the `OkHttpClient` and you then observe a similar trace as the example just above but with the client as the originating root span.

Cross process tracing is working thanks to headers injected on the client side that are extracted on the server. If you want to understand more you can refer to the [opentracing documentation](http://opentracing.io/documentation/pages/api/cross-process-tracing.html).

*A trace example*

![](./apm.png)

To run the distributed example, first start the dropwizard app, the mongo db and finally run the `main` from `com.example.helloworld.client.TracedClient` 

### How did we instrument this project?

#### Auto-instrumentation with the `dd-trace-agent`

The instrumentation is entirely done by the datadog agent which embed a set of rules that automatically recognizes & instruments:

- The Java servlet filters
- The Mongo client
- The OkHTTP client
- The `@Trace` annotation

The Datadog agent embeds the [open tracing java agent](https://github.com/opentracing-contrib/java-agent).
We strongly recommend you to refer to the [Datadog Agent documentation](../../dd-java-agent) if you want to dig deeper.
