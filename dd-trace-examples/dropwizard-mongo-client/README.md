## Dropwizard

This project provides a simple [Dropwizard][1] example. This is a supported framework that uses
auto-instrumentation for all endpoints. Manual instrumentation has been added as example.

[1]: http://www.dropwizard.io/

### Run the demo

#### Prerequisites

Be sure to build the project so that the latest version of ``dd-trace-java`` components are used. You can build
all libraries and examples launching from the ``dd-trace-java`` root folder:
```bash
./gradlew clean shadowJar
```

Then you can start all services via Docker:
```bash
cd dd-trace-examples/dropwizard-mongo-client
DD_API_KEY=<your_datadog_api_key> docker-compose up -d
```

A valid ``DD_API_KEY`` is required to post collected traces to the Datadog backend.

#### Run the application

Launch the application using the run wrapper you've built during the ``installDist`` step:
```bash
JAVA_OPTS=-javaagent:../../dd-java-agent/build/libs/dd-java-agent-{version}.jar build/install/dropwizard-mongo-client/bin/dropwizard-mongo-client server
```

``0.2.0-SNAPSHOT`` is an example of what ``{version}`` looks like.

### Generate traces

#### With your web browser

Once the application runs. Go to the following url:

* [http://localhost:8080/demo/add?title=some-book-title&isbn=1234&page=42][1]
* [http://localhost:8080/demo/][2]

[1]: http://localhost:8080/demo/add?title=some-book-title&isbn=1234&page=42
[2]: http://localhost:8080/demo/

Then get back to Datadog and wait a bit to see a trace coming.

#### Cross process tracing: with the provided `TracedClient` class

The ``TracedClient`` class includes an example of what you can use to do distributed tracing. The class must be
auto-instrumented with the Java Agent as above, so that ``OkHttpClient`` adds the required headers to continue
the tracing cross process.

To run the distributed example, first start the dropwizard app, the mongo db and finally run the `main` from `com.example.helloworld.client.TracedClient` 

#### Auto-instrumentation with the `dd-trace-agent`

The instrumentation is entirely done by the Java Agent which embed a set of rules that automatically recognizes & 
instruments:

- The Java servlet filters
- The Mongo client
- The `@Trace` annotation
- The OkHTTP client (in the ``TracedClient`` class)

The Java Agent embeds the [OpenTracing Java Agent](https://github.com/opentracing-contrib/java-agent).
