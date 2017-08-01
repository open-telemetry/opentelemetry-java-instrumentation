## REST Spark

This project provides a simple REST API using the [Spark web framework][1]. Even if the framework is not directly
supported, manual instrumentation is used to trace one of the endpoints. A ``MongoClient`` is traced so that
Mongo calls are part of the Spark trace.

[1]: http://sparkjava.com/

### Run the demo

#### Prerequisites

Be sure to build the project so that the latest version of ``dd-trace-java`` components are used. You can build
all libraries and examples launching from the ``dd-trace-java`` root folder:
```bash
./gradlew clean shadowJar
```

Then you can prepare the distributable version of the ``rest-spark`` as follows:
```bash
cd dd-trace-examples/rest-spark
./gradlew installDist
```

Then you can start all services via Docker:
```bash
DD_API_KEY=<your_datadog_api_key> docker-compose up -d
```

A valid ``DD_API_KEY`` is required to post collected traces to the Datadog backend.

#### Run the application

Launch the application using the run wrapper you've built during the ``installDist`` step:
```bash
JAVA_OPTS=-javaagent:../../dd-java-agent/build/libs/dd-java-agent-{version}.jar build/install/rest-spark/bin/rest-spark
```

``0.1.2-SNAPSHOT`` is an example of what ``{version}`` looks like.

### Generate traces

#### With your web browser

Once the application runs. Go to the following url:

* [http://localhost:4567/key/something][2]
* [http://localhost:4567/key/something_else][3]

Then get back to Datadog and wait a bit to see a trace coming.

[2]: http://localhost:4567/key/something
[3]: http://localhost:4567/key/something_else

#### Auto-instrumentation with the `dd-trace-agent`

The instrumentation is entirely done by the Java Agent which embed a set of rules that automatically recognizes &
instruments:

- The Mongo client

The Java Agent embeds the [OpenTracing Java Agent](https://github.com/opentracing-contrib/java-agent).
