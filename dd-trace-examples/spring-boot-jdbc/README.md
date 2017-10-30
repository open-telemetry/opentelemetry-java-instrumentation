## Spring-boot 

This project provides a simple API using [Spring Boot][1]. The framework is supported and auto-instrumentation is used
to trace the endpoints.

[1]: https://projects.spring.io/spring-boot/
 
### Run the demo

#### Prerequisites

Be sure to build the project so that the latest version of ``dd-trace-java`` components are used. You can build
all libraries and examples launching from the ``dd-trace-java`` root folder:
```bash
./gradlew clean shadowJar bootRepackage
```

Then you can launch the Datadog agent as follows:
```bash
cd dd-trace-examples/spring-boot-jdbc
DD_API_KEY=<your_datadog_api_key> docker-compose up -d
```

A valid ``DD_API_KEY`` is required to post collected traces to the Datadog backend.

#### Run the application

To launch the application, just:
```bash
./gradlew bootRun
```

*Note: The ``bootRun`` Gradle command appends automatically the ``-javaagent`` argument, so that you don't need to specify
the path of the Java Agent. Gradle executes the ``:dd-trace-examples:spring-boot-jdbc:bootRun`` task until you
stop it.*

Or as an executable jar:
```bash
java -javaagent:../../dd-java-agent/build/libs/dd-java-agent-{version}.jar -jar build/libs/spring-boot-jdbc-demo.jar
```

### Generate traces

Once the Gradle task is running. Go to the following urls:

* [http://localhost:8080/user/add?name=foo&email=bar](http://localhost:8080/user/add?name=foo&email=bar)
* [http://localhost:8080/user/all](http://localhost:8080/user/all)

Then get back to Datadog and wait a bit to see a trace coming.

#### Auto-instrumentation with the `dd-trace-agent`

The instrumentation is entirely done by the datadog agent which embed a set of rules that automatically recognizes &
instruments:

- The java servlet filters
- The JDBC driver

The Java Agent embeds the [OpenTracing Java Agent](https://github.com/opentracing-contrib/java-agent).
