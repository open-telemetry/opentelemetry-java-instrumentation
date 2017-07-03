# Datadog Opentracing Tracer

## Motivations

The Datadog Tracer is an Opentracing compatible tracer. It provides all resources needed to instrument your code
and report each operations, each traces directly to a Datadog APM platform.


Opentracing uses the concept of the **span**. A span is **timed operation** representing a bunch of work executed.
Spans can **be linked together**. And a **trace** is a collection of spans, related to the same top action/operation.

Let's see an example. 

For instance, a client requesting a resource through an HTTP endpoint.
Look at the following workflow.

````
TRACE:

   client ---> HTTP Endpoint ---> DB Query/Result ---> Custom processing ---> client
  
   SPAN 1 (Root Span) ................................................... (end)   - 200ms
   |--------SPAN 2 (Child of 1).................................(end)             - 100ms
            |-----------SPAN 3 (Child of 2).(end)                                 - 50 ms
            |----------------------------------------SPAN 4 (Child of 2)..(end)   - 50 ms
````

Opentracing provides a way for measuring the time consumed for each operation.
As just described, the tracer produces a trace composed of 4 spans, each representing a specific action:

1. Span1 is the time from doing the request to getting the response.
2. Span2 is the Span1's first child, representing the amount of time to understand the query, and perform the query
on the DB.
3. Span3, a Span1' grandchild, represents the DB time used to retrieve the data
4. Span4 is a child of Span2 and followed Span3. It represents a business/legacy operation.

This is  a very simple example of how works [Opentracing](http://opentracing.io/).
To dig deeper, read the full documentation: http://opentracing.io/


## How to instrument your application?

There are 3 ways to instrument an application:
1. [Use the autotracing agent for supported frawemorks](#framework)
2. [Use the Opentracing API](#api)
3. [Use annotations](#annotation)
 
### <a name="framework"></a>Use the autotracing agent for well-known framework

Datadog instruments many frameworks and libraries by default: SpringBoot, JDBC, Mongo, JMS, Tomcat, etc.
By using the autotracing agent, you just need to follow few steps in order to get traces. 

Check the dedicated project and agent: [dd-java-agent](../dd-java-agent)


### <a name="api"></a>Custom instrumentations using Opentracing API

If you want to add custom instrumenting to your code, you have to use the Opentracing API.
The official documentation can be found right here: [](https://github.com/opentracing/opentracing-java).

Let's look at a simple example.


```java
class InstrumentedClass {

    
    void methodSDK() {
        // Retrieve the tracer using the resolver provided
        // Make sure you have :
        //    1. added the agent to the jvm (-javaagent;/path/to/agent.jar)
        //    2. a dd-trace.yaml file in your resources directory
        Tracer tracer = io.opentracing.util.GlobalTracer.get();
        
        Span span = tracer.buildSpan("operation-name").startActive();
        
        //Do some thing here ...
        Thread.sleep(1_000);
        
        // Close the span, the trace will automatically reported to the writer configured
        span.finish();   
    }	
	
}
``` 

The method above is now instrumented. As you can see, the tracer is retrieved from a global registry, called `GlobalTracer`.

The last thing you have to do is providing a configured tracer. This can be easily done by using the `TracerFactory`.
in the bootstrap method (like the `main`).

```java
public class Application {

    public static void main(String[] args) {
	
        // Init the tracer from the configuration file      
        Tracer tracer = DDTracerFactory.createFromConfigurationFile();
        io.opentracing.util.GlobalTracer.register(tracer);
        
        // ...
    }
}
```

The factory looks for a `dd-trace.yaml` file in the classpath. The DDTracer is auto-configured using this YAML file.
 
By default, the DDTracer tries to reach a local Datadog Agent, but you can change the settings and use a different
location. In order to do that, please, refer you to the latest configuration: [dd-trace.yaml](src/main/resources/dd-trace.yaml)

```yaml
# Service name used if none is provided in the app
defaultServiceName: unnamed-java-app

# The writer to use.
# Could be: LoggingWritter or DDAgentWriter (default)
writer:
  # LoggingWriter: Spans are logged using the application configuration
  # DDAgentWriter: Spans are forwarding to a Datadog Agent
  #  - Param 'host': the hostname where the DD Agent running (default: localhost)
  #  - Param 'port': the port to reach the DD Agent (default: 8126)
  type: DDAgentWriter
  host: localhost
  port: 8126

# The sampler to use.
# Could be: AllSampler (default) or RateSampler
sampler:
  # AllSampler: all spans are reported to the writer
  # RateSample: only a portion of spans are reported to the writer
  #  - Param 'rate': the portion of spans to keep
  type: AllSampler
```

Do not forget to add the corresponding dependencies to your project.


```xml
        <!-- Opentracing API -->
        <dependency>
            <groupId>io.opentracing</groupId>
            <artifactId>opentracing-api</artifactId>
            <version>${opentracing.version}</version>
        </dependency>
        
        <!-- Datadog Tracer (only useful if you do not use the Datadog autotracing agent) -->
        <dependency>
            <groupId>com.datadoghq</groupId>
            <artifactId>dd-trace</artifactId>
            <version>${dd-trace-java.version}</version>
        </dependency>
```


### <a name="annotation"></a>Custom instrumentations using Annotation

Datadog provides a third way to instrument your code: annotations.
The following example is the same as above. Just add `@Trace` to the methods you want to instrument.

```java
class InstrumentedClass {

    @Trace(operationName = "operation-name")
    void methodSDK() {

        //Do some thing here ...
        Thread.sleep(1_000);
    }	
}
```

In order to use annotations, the only required dependency is that package.
```xml
        <!-- Datadog annotations -->
        <dependency>
            <groupId>com.datadoghq</groupId>
            <artifactId>dd-trace-annotations</artifactId>
            <version>${dd-trace-java.version}</version>
        </dependency>
```
The annotations are resolved at the runtime by the autotracing agent. If you want to use the annotations,
so have to provide the agent.

To attach the agent to the JVM, you simply have to declare the provided `jar` file in your 
JVM arguments as a valid `-javaagent`. Don't forget to replace the `{version}` placeholder in the following commands.

So first download the `jar` file from the main Maven repository: http://central.maven.org/maven2/com/datadoghq/dd-java-agent/

```
> curl -OL http://central.maven.org/maven2/com/datadoghq/dd-java-agent/{version}/dd-java-agent-{version}.jar   
```
Then add the following JVM argument when launching your application (in IDE, using Maven run or simply in collaboration with the `>java -jar` command):

```
-javaagent:/path/to/dd-java-agent-{version}.jar   
```

At this point, the DDTrace is loaded in the project.


## Other useful resources

Before instrumenting your own project you might want to run the provided examples:

- [Dropwizard/MongoDB & Cross process client calls](https://github.com/DataDog/dd-trace-java/blob/dev/dd-trace-examples/dropwizard-mongo-client/)
- [Springboot & MySQL over JDBC](https://github.com/DataDog/dd-trace-java/tree/dev/dd-trace-examples/spring-boot-jdbc)

Other links that you might want to read:

- Install on [Docker](https://app.datadoghq.com/apm/docs/tutorials/docker)
- Datadog's APM [Terminology](https://app.datadoghq.com/apm/docs/tutorials/terminology)
- [FAQ](https://app.datadoghq.com/apm/docs/tutorials/faq)


And for any questions, feedback, feel free to send us an email: support@datadoghq.com