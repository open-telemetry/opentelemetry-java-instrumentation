## Datadog Java Tracer

### Motivations

The Datadog Java Tracer is an OpenTracing-compatible tracer. It provides all resources needed to instrument your code.
Opentracing introduces the concept of the *span*. A span is *timed operation* representing an action in the code.
The span can *be linked together*. And a trace is a list of spans, each related to the same top action/operation.

Let's see an example. When a client does a request on a Http Endpoint, asking for some BD resources, 
Opentracing provides a way for measuring the time consumed for each operation:

````
TRACE:

   client ---> HTTP Endpoint ---> DB Query/Result ---> Custom processing ---> client
  
   SPAN 1 (Root Span) ................................................... (end)   - 200ms
   |--------SPAN 2 (Child of 1).................................(end)             - 100ms
            |-----------SPAN 3 (Child of 2).(end)                                 - 50 ms
            |----------------------------------------SPAN 4 (Child of 2)..(end)   - 50 ms
````

This shows a very simple example of how works [Opentracing](http://opentracing.io/).
Here, the tracer produces a trace composed of 4 spans, each representing a specific action.


### How to load the Datadog Tracer (DDTrace) in the project?

The current implementation uses the trace-resolver feature provides by Opentracing.
That means you can add and load the tracer using a Java Agent directly with the JVM.

And then, the DDTrace can be configured using a YAML file: `dd-trace.yaml`.
By default, the DDTrace tries to reach a local Datadog Agent, but it can change by settings a different
location in the config file. Please, refer to the latest configuration template: [dd-trace.yaml](src/main/resources/dd-trace.yaml)


### How to use the Datadog Tracer (DDTrace) to instrument the code?

Once, the DDTrace is loaded, you can start to instrument your code using:
* The `@Trace` annotation,
* Or the Opentracing SDK.





























This agent is build on the top of the [Open-Tracing contributions](https://github.com/opentracing-contrib/) and the [Datadog
tracer](https://github.com/DataDog/dd-trace-java).

At the moment, the Datadog Java Agent supports the following framework and librairies:

* Databases
  * JDBC connections
  * Mongo
  * Elasticsearch
* Web servers and clients
  * Spring Boot
  * Jetty Server
  * Tomcat Servlet
  * Apache HTTP Client
  * OkHttp
* Queueing
  * JMS
* Third-party
  * AMS SDK Client 
  
#### Instrument your code
  
Here are the instructions.
Before start, make sure you have a running Datadog Agent with the [APM feature enabled](http://docs.datadoghq.com/tracing/).

1. Download the latest Datadog Java Agent version: https://mvnrepository.com/artifact/com.datadoghq/dd-java-agent.
```pom.xml
<dependency>
    <groupId>com.datadoghq</groupId>
    <artifactId>dd-java-agent</artifactId>
    <version>${dd-trace.version}</version>
</dependency>
```
2. Add to the JVM the agent. This can be done by editing the command line or via the pom file. 
```pom.xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>2.20</version>
    <configuration>
        <includes>
            <include>*Test.java</include>
        </includes>
        <excludes>
            <exclude>ElasticSearchInstrumentationTest.java</exclude>
        </excludes>
        <forkCount>3</forkCount>
        <reuseForks>false</reuseForks>
        <argLine>-javaagent:${M2_REPO}/com/datadoghq/dd-java-agent/${dd-trace.version}/dd-java-agent-${dd-trace.version}.jar</argLine>
        <workingDirectory>target/FORK_DIRECTORY_${surefire.forkNumber}</workingDirectory>
    </configuration>
</plugin>

```


### How it's work
### Dig deeper


Du coup le Readme.md de `java-trace` il faut un truc plutot simple qui expliqe que c la 
librarie opentracing compliant de Datadog. Il faut expliquer aussi le resolver et son fichier `.yaml`

[10:41] 
Prend peut etre exemple aussi sur une autre lib opentracing compliant

[10:41] 
Peut etre expliquer ce que c’est un tracer, un span et un ou deux exemple d’instrumentation a la mano¡¡