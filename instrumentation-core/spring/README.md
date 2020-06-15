# OpenTelemetry Instrumentation: Spring and Spring Boot
<!-- ReadMe is in progress -->
<!-- TO DO: Add sections for starter guide -->

This package streamlines the manual instrumentation process of OpenTelemetry for [Spring](https://spring.io/projects/spring-framework) and [Spring Boot](https://spring.io/projects/spring-boot) applications. It will enable you to add traces to requests and database calls with minimal changes to application code. This package will not fully automate your OpenTelemetry instrumentation, instead, it will provide you with better tools to instrument your own code. 

The [first section](#manual-instrumentation-with-java-sdk) will walk you through span creation and propagation using the OpenTelemetry Java API and [Spring's RestTemplate Http Web Client](https://spring.io/guides/gs/consuming-rest/). This approach will use the "vanilla" OpenTelemetry API to make explicit tracing calls within an application's controller. 

The second section will build on the first. It will walk you through implementing spring-web handler and interceptor interfaces to create traces with minimal changes to existing application code. Using the OpenTelemetry API, this approach involves copy and pasting files and a significant amount of manual configurations. 

The third section will walk you through the annotations and configurations defined in the opentelemetry-contrib-spring package. This section will equip you with new tools to streamline the set up and instrumentation of OpenTelemetry on Spring and Spring Boot applications. With these tools you will be able to setup distributed tracing with little to no changes to existing configurations and easily customize traces with minor additions to application code.

In this guide we will be using a running example. In section one and two, we will create two spring web services using Spring Boot. We will then trace the requests between these services using two different approaches. Finally, in section three we will explore tools in the opentelemetry-instrumentation-spring package which can improve this process.

# Manual Instrumentation Guide

## Create two Spring Projects

Using the [spring project initializer](https://start.spring.io/), we will create two spring projects.  Name one project `FirstService` and the other `SecondService`. Make sure to select maven, Spring Boot 2.3, Java, and add the spring-web dependency. After downloading the two projects include the OpenTelemetry dependencies and configuration listed below. 

## Setup for Manual Instrumentation

Add the dependencies below to enable OpenTelemetry in `FirstService` and `SecondService`. The Jaeger and LoggingExporter packages are recommended for exporting traces but are not required. As of May 2020, Jaeger, Zipkin, OTLP, and Logging exporters are supported by opentelemetry-java. Feel free to use whatever exporter you are most comfortable with. 

### Maven
 
#### OpenTelemetry
```xml
<dependency>
	<groupId>io.opentelemetry</groupId>
	<artifactId>opentelemetry-api</artifactId>
	<version>0.5.0</version>
</dependency>
<dependency>
	<groupId>io.opentelemetry</groupId>
	<artifactId>opentelemetry-sdk</artifactId>
	<version>0.5.0</version>
</dependency>	
<dependency>
    <groupId>io.grpc</groupId>
    <artifactId>grpc-context</artifactId>
    <version>1.24.0</version>
</dependency>

```

#### LoggingExporter
```xml
<dependency>
	<groupId>io.opentelemetry</groupId>
	<artifactId>opentelemetry-exporters-logging</artifactId>
	<version>0.5.0</version>
</dependency>
```

#### JaegerExporter
```xml
<dependency>
	<groupId>io.opentelemetry</groupId>
	<artifactId>opentelemetry-exporters-jaeger</artifactId>
	<version>0.5.0</version>
</dependency>
<dependency>
	<groupId>io.grpc</groupId>
	<artifactId>grpc-protobuf</artifactId>
	<version>1.27.2</version>
</dependency>
<dependency>
	<groupId>io.grpc</groupId>
	<artifactId>grpc-netty</artifactId>
	<version>1.27.2</version>
</dependency>
```

### Gradle
 
#### OpenTelemetry
```gradle
compile "io.opentelemetry:opentelemetry-api:0.5.0"
compile "io.opentelemetry:opentelemetry-sdk:0.5.0"
compile "io.grpc:grpc-context:1.24.0"
```

#### LoggingExporter
```gradle
compile "io.opentelemetry:opentelemetry-exporters-logging:0.5.0"
```

#### JaegerExporter
```gradle
compile "io.opentelemetry:opentelemetry-exporters-jaeger:0.5.0"
compile "io.grpc:grpc-protobuf:1.27.2"
compile "io.grpc:grpc-netty:1.27.2"
```

### Tracer Configuration

To enable tracing in your OpenTelemetry project configure a Tracer Bean. This bean will be auto wired to controllers to create and propagate spans. This can be seen in the `Tracer otelTracer()` method below. If you plan to use a trace exporter remember to also include it in this configuration class. In section 3 we will use an annotation to set up this configuration.

A sample OpenTelemetry configuration using LoggingExporter is shown below: 

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.grpc.ManagedChannelBuilder;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.trace.Tracer;
import io.opentelemetry.exporters.jaeger.JaegerGrpcSpanExporter;
import io.opentelemetry.exporters.logging.*;

@Configuration
public class OtelConfig {
  private static tracerName = "foo"; \\TODO:
  @Bean
  public Tracer otelTracer() throws Exception {
    final Tracer tracer = OpenTelemetry.getTracer(tracerName);

    SpanProcessor logProcessor = SimpleSpanProcessor.newBuilder(new LoggingSpanExporter()).build();
    OpenTelemetrySdk.getTracerProvider().addSpanProcessor(logProcessor);
    
    return tracer;
  }
}
```


The file above configures an OpenTelemetry tracer and a span processor. The span processor builds a log exporter which will output spans to the console. Similarly, one could add another exporter, such as the `JaegerExporter`, to visualize traces on a different back-end. Similar to how the `LoggingExporter` is configured, a Jaeger configuration can be added to the `OtelConfig` class above. 

Sample configuration for a Jaeger Exporter:

```java

SpanProcessor jaegerProcessor = SimpleSpanProcessor
        .newBuilder(JaegerGrpcSpanExporter.newBuilder().setServiceName(tracerName)
            .setChannel(ManagedChannelBuilder.forAddress("localhost", 14250).usePlaintext().build())
            .build())
        .build();
OpenTelemetrySdk.getTracerProvider().addSpanProcessor(jaegerProcessor);
```
     
### Project Background

Here we will create rest controllers for `FirstService` and `SecondService`.
`FirstService` will send a GET request to `SecondService` to retrieve the current time. After this request is resolved, `FirstService` then will append a message to time and return a string to the client. 

## Manual Instrumentation with Java SDK

### Add OpenTelemetry to FirstService and SecondService

Required dependencies and configurations for FirstService and SecondService projects can be found [here](#setup-for-manual-instrumentation).

### FirstService

1. Ensure OpenTelemetry dependencies are included
2. Ensure an OpenTelemetry Tracer is configured

3. Ensure a Spring Boot main class was created by the Spring initializer

```java
@SpringBootApplication
public class FirstServiceApplication {

  public static void main(String[] args) throws IOException {
    SpringApplication.run(FirstServiceApplication.class, args);
  }
}
```

4. Create a RestController for FirstService
5. Create a span to wrap the FirstServiceController

```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;

import HttpUtils;

@RestController
@RequestMapping(value = "/message")
public class FirstServiceController {
  @Autowired
  private Tracer tracer;

  @Autowired
  HttpUtils httpUtils;

  private static String SS_URL = "http://localhost:8081/time";

  @GetMapping
  public String firstTracedMethod() {
    Span span = tracer.spanBuilder("message").startSpan();
    span.addEvent("Controller Entered");
    span.setAttribute("what.are.you", "Je suis attribute");

    try (Scope scope = tracer.withSpan(span)) {
      return "Second Service says: " + httpUtils.callEndpoint(SS_URL);
    } catch (Exception e) {
      span.setAttribute("error", e.toString());
      span.setAttribute("error", true);
      return "ERROR: I can't tell the time";
    } finally {
      span.end();
    }
  }
}
```

6. Configure `HttpUtils.callEndpoint` to inject span context into request. This is key to propagate the trace to the SecondService

HttpUtils is a helper class that injects the current span context into outgoing requests. This involves adding the tracer id and the trace-state to a request header. For this example, I used `RestTemplate` to send requests from `FirstService` to `SecondService`. A similar approach can be used with popular Java Web Clients such as [okhttp](https://square.github.io/okhttp/) and [apache http client](https://www.tutorialspoint.com/apache_httpclient/apache_httpclient_quick_guide.htm). The key to this implementation is to override the put method in `HttpTextFormat.Setter<?>` to handle your request format. `HttpTextFormat.inject` will use this setter to set `traceparent` and `tracestate` headers in your requests. These values will be used to propagate your span context to external services.

```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import io.opentelemetry.context.propagation.HttpTextFormat;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.SpanContext;
import io.opentelemetry.trace.Tracer;

@Component
public class HttpUtils {

  @Autowired
  private Tracer tracer;

  private HttpTextFormat<SpanContext> textFormat;
  private HttpTextFormat.Setter<HttpHeaders> setter;

  public HttpUtils(Tracer tracer) {
    textFormat = tracer.getHttpTextFormat();
    setter = new HttpTextFormat.Setter<HttpHeaders>() {
      @Override
      public void put(HttpHeaders headers, String key, String value) {
        headers.set(key, value);
      }
    };
  }

  public String callEndpoint(String url) throws Exception {
    HttpHeaders headers = new HttpHeaders();

    Span currentSpan = tracer.getCurrentSpan();
    textFormat.inject(currentSpan.getContext(), headers, setter);

    HttpEntity<String> entity = new HttpEntity<String>(headers);
    RestTemplate restTemplate = new RestTemplate();

    ResponseEntity<String> response =
        restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

    return response.getBody();
  }
}
```
### SecondService

1. Ensure OpenTelemetry dependencies are included
2. Ensure an OpenTelemetry Tracer is configured
3. Ensure a Spring Boot main class was created by the Spring initializer

  
```java
import java.io.IOException;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SecondServiceApplication {

  public static void main(String[] args) throws IOException {
    SpringApplication.run(SecondServiceApplication.class, args);
  }
}
```

4. Create a RestController for SecondService
5. Start a span to wrap the SecondServiceController

```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;

@RestController
@RequestMapping(value = "/time")
public class SecondServiceController {
  @Autowired
  private Tracer tracer;

  @GetMapping
  public String callSecondTracedMethod() {
    Span span = tracer.spanBuilder("time").startSpan();
    span.addEvent("SecondServiceController Entered");
    span.setAttribute("what.am.i", "Tu es une legume");

    try (Scope scope = tracer.withSpan(span)) {
      return "It's time to get a watch";
    } finally {
      span.end();
    }
  }
}
```

### Run FirstService and SecondService

***To view your distributed traces ensure either LogExporter or Jaeger is configured in the OtelConfig.java file*** 

To view traces on the Jaeger UI, deploy a Jaeger Exporter on localhost by running the command in terminal:

`docker run --rm -it --network=host jaegertracing/all-in-one` 

After running Jaeger locally, navigate to the url below. Make sure to refresh the UI to view the exported traces from the two web services:

`http://localhost:16686`
 
Run FirstService and SecondService from command line or using an IDE. The end point of interest for FirstService is `http://localhost:8080/message` and  `http://localhost:8081/time` for SecondService. Entering `localhost:8080/time` in a browser should call FirstService and then SecondService, creating a trace. To send a sample request enter the following in a browser of your choice:

`http://localhost:8080/message`

***Note: The default port for the Apache Tomcat is 8080. On localhost both FirstService and SecondService services will attempt to run on this port raising an error. To avoid this add `server.port=8081` to the resources/application.properties file. Ensure the port specified corresponds to port referenced by FirstServiceController.SECOND_SERVICE_URL. ***

Congrats, we just created a distributed service with OpenTelemetry!  
