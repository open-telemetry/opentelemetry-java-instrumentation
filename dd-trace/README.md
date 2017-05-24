## Datadog Java Tracer

### Motivations

The Datadog Java Tracer is an OpenTracing-compatible tracer. It provides all resources needed to instrument your code.


Opentracing introduces the concept of the **span**. A span is **timed operation** representing "a bounded process" in the code.
The spans can **be linked together**. And a **trace** is a list of spans, each related to the same top action/operation.

Let's see an example. 

The workflow can be a client requesting, via a HTTP endpoint, some resources store in a DB.
Look at the following scheme.

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
4. Span4 is a child of Span2 and followed Span3. It represents a business process for instance.

This is  a very simple example of how works [Opentracing](http://opentracing.io/).
Do not hesitate to go deeper and read the full documentation: http://opentracing.io/


### How to instrument well-known framework?

Datadog instruments many frameworks and libraries by default: SpringBoot, JDBC, Mongo, JMS, Tomcat, etc. 
Check the dedicated project and agent: [dd-java-agent](../dd-java-agent)


### How the Datadog Tracer (DDTrace) is loaded in the project?

This current implementation uses the trace-resolver feature provides by Opentracing.
That means you can add and load the tracer using a Java Agent directly with the JVM.

The DDTrace is autoconfigured using the YAML file provided in the project: `dd-trace.yaml`. 
By default, the DDTrace tries to reach a local Datadog Agent, but you can change the settings and use a different
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

To attach the agent to the JVM, you simply have to declare the provided `jar` file in your 
JVM arguments as a valid `-javaagent:`. We assume that your `${M2_REPO}` env variable is properly set.
Don't forget to replace the `{version}` placeholder in the following commands.

So first download the `jar` file from the main Maven repository:

```
> mvn dependency:get -Dartifact=io.opentracing-contrib:opentracing-agent:${version}
```
Then add the following JVM argument when launching your application (in IDE, using Maven run or simply in collaboration with the `>java -jar` command):

```
-javaagent:${M2_REPO}/io/opentracing-contrib/opentracing-agent/${version}/opentracing-agent-${version}.jar
```


At this point, the DDTrace is loaded in the project. Let's see now how to instrument it.

### How to use the Datadog Tracer (DDTrace) for instrumenting legacy code?

Once, the DDTrace is loaded, you can start to instrument your code using the Opentracing SDK or the `@Trace` annotation.
`@Trace` is actually a Datadog specific, but we plan to submit it to Opentracing foundation. 

To use them, you have to add the dependency to the DDTrace.
Just edit you `pom.xml` and add this:

```xml
    <dependency>
        <groupId>com.datadoghq</groupId>
        <artifactId>dd-trace</artifactId>
        <version>${dd-trace-java.version}</version>
    </dependency>
```


You can start as shown below, here is an example how to use both of them to instrument 2 simple methods.

```java
class InstrumentedClass {
	
	@Trace
	void methodAnnoted() {
		// The annotation will do the same thing as the manual instrumentation below
		//Do some thing here ...
		Thread.sleep(1_000);
	}
	
	void methodSDK() {
        // Retrieve the tracer using the resolver provided
        // Make sure you have :
        //    1. added the agent to the jvm (-javaagent;/path/to/agent.jar)
        //    2. a dd-trace.yaml file in your resources directory
        Tracer tracer = io.opentracing.util.GlobalTracer.get();
        
        Span span = tracer.buildSpan("operation-name").build();
        
        //Do some thing here ...
        Thread.sleep(1_000);
        
        // Close the span, the trace will automatically reported to the writer configured
        span.close();   
	}	
	
}
```

If you have a running Datadog Agent with the [APM feature enabled](http://docs.datadoghq.com/tracing/), you should
see traces directly to your Datadog account.



### Other useful resources

Before instrumenting your own project you might want to run the provided examples:

- [Dropwizard/MongoDB & Cross process client calls](https://github.com/DataDog/dd-trace-java/blob/dev/dd-trace-examples/dropwizard-mongo-client/)
- [Springboot & MySQL over JDBC](https://github.com/DataDog/dd-trace-java/tree/dev/dd-trace-examples/spring-boot-jdbc)

Other links that you might want to read:

- Install on [Docker](https://app.datadoghq.com/apm/docs/tutorials/docker)
- Datadog's APM [Terminology](https://app.datadoghq.com/apm/docs/tutorials/terminology)
- [FAQ](https://app.datadoghq.com/apm/docs/tutorials/faq)


And for any questions, feedback, feel free to send us an email: support@datadoghq.com