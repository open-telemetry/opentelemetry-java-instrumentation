# OpenTelemetry Instrumentation: Spring and Spring Boot
<!-- ReadMe is in progress -->
<!-- TO DO: Add sections for starter guide -->

This package streamlines the manual instrumentation process of OpenTelemetry for [Spring](https://spring.io/projects/spring-framework) and [Spring Boot](https://spring.io/projects/spring-boot) applications. It will enable you to add traces to requests and database calls with minimal changes to application code. This package will not fully automate your OpenTelemetry instrumentation, instead, it will provide you with better tools to instrument your own code. 

The [first section](#manual-instrumentation-with-java-sdk) will walk you through span creation and propagation using the OpenTelemetry Java API and [Spring's RestTemplate Http Web Client](https://spring.io/guides/gs/consuming-rest/). This approach will use the "vanilla" OpenTelemetry API to make explicit tracing calls within an application's controller. 

The [second section](#manual-instrumentation-using-handlers-and-filters)  will build on the first. It will walk you through implementing spring-web handler and filter interfaces to create traces with minimal changes to existing application code. Using the OpenTelemetry API, this approach involves copy and pasting files and a significant amount of manual configurations. 

The third section will walk you through the annotations and configurations defined in the opentelemetry-instrumentation-spring package. This section will equip you with new tools to streamline the setup and instrumentation of OpenTelemetry on Spring and Spring Boot applications. With these tools you will be able to setup distributed tracing with little to no changes to existing configurations and easily customize traces with minor additions to application code.

In this guide we will be using a running example. In section one and two, we will create two spring web services using Spring Boot. We will then trace the requests between these services using two different approaches. Finally, in section three we will explore tools in the opentelemetry-instrumentation-spring package which can improve this process.

# Manual Instrumentation Guide

## Create two Spring Projects

Using the [spring project initializer](https://start.spring.io/), we will create two spring projects.  Name one project `FirstService` and the other `SecondService`. In this example `FirstService` will be a client of `SecondService` and they will be dealing with time. Make sure to select maven, Spring Boot 2.3, Java, and add the spring-web dependency. After downloading the two projects include the OpenTelemetry dependencies and configuration listed below. 

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
   private static final tracerName = "fooTracer"; 
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

Here we will create REST controllers for `FirstService` and `SecondService`.
`FirstService` will send a GET request to `SecondService` to retrieve the current time. After this request is resolved, `FirstService` then will append a message to time and return a string to the client. 

## Manual Instrumentation with Java SDK

### Add OpenTelemetry to FirstService and SecondService

Required dependencies and configurations for FirstService and SecondService projects can be found [here](#setup-for-manual-instrumentation).

### Instrumentation of Receiving Service: FirstService

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

4. Create a REST controller for FirstService
5. Create a span to wrap FirstServiceController.firstTracedMethod()

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
   private static int requestCount = 1;
   
   @Autowired
   private Tracer tracer;

   @Autowired
   private HttpUtils httpUtils;

   private static final String SECOND_SERVICE_URL = "http://localhost:8081/time";

   @GetMapping
   public String firstTracedMethod() {
      Span span = tracer.spanBuilder("message").startSpan();
      span.addEvent("Controller Entered");
      span.setAttribute("firstservicecontroller.request.count", requestCount++);

      try (Scope scope = tracer.withSpan(span)) {
         return "Second Service says: " + httpUtils.callEndpoint(SECOND_SERVICE_URL);
      } catch (Exception e) {
         span.setAttribute("error", true);
         return "ERROR: I can't tell the time";
      } finally {
         span.addEvent("Exit Controller");
         span.end();
      }
   }
}
```

6. Configure `HttpUtils.callEndpoint` to inject span context into request. This is key to propagate the trace to the SecondService

HttpUtils is a helper class that injects the current span context into outgoing requests. This involves adding the tracer id and the trace-state to a request header. For this example, we used `RestTemplate` to send requests from `FirstService` to `SecondService`. A similar approach can be used with popular Java Web Clients such as [okhttp](https://square.github.io/okhttp/) and [apache http client](https://www.tutorialspoint.com/apache_httpclient/apache_httpclient_quick_guide.htm). The key to this implementation is to override the put method in `HttpTextFormat.Setter<?>` to handle your request format. `HttpTextFormat.inject` will use this setter to set `traceparent` and `tracestate` headers in your requests. These values will be used to propagate your span context to external services.


```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import io.grpc.Context;

import io.opentelemetry.context.propagation.HttpTextFormat;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.SpanContext;
import io.opentelemetry.trace.Tracer;

@Component
public class HttpUtils {

   private static final HttpTextFormat.Setter<HttpHeaders> setter = new HttpTextFormat.Setter<HttpHeaders>() {
         @Override
         public void set(HttpHeaders headers, String key, String value) {
            headers.set(key, value);
         }
      };
      
   @Autowired
   private Tracer tracer;

   private HttpTextFormat<SpanContext> textFormat;

   public HttpUtils(Tracer tracer) {
      textFormat = tracer.getHttpTextFormat();
   }

   public String callEndpoint(String url) throws Exception {
      HttpHeaders headers = new HttpHeaders();

      textFormat.inject(Context.current(), headers, setter);

      HttpEntity<String> entity = new HttpEntity<String>(headers);
      RestTemplate restTemplate = new RestTemplate();

      ResponseEntity<String> response =
            restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

      return response.getBody();
   }
}
```
### Instrumentation of Client Service: SecondService

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

4. Create a REST controller for SecondService
5. Start a span to wrap SecondServiceController.secondTracedMethod()

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
   public String secondTracedMethod() {
      Span span = tracer.spanBuilder("time").startSpan();
      span.addEvent("SecondServiceController Entered");
      span.setAttribute("what.am.i", "Tu es une legume");

      try{
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

Run FirstService and SecondService from command line or using an IDE. The end point of interest for FirstService is `http://localhost:8080/message` and  `http://localhost:8081/time` for SecondService. Entering `localhost:8080/message` in a browser should call FirstService and then SecondService, creating a trace. 

***Note: The default port for the Apache Tomcat is 8080. On localhost both FirstService and SecondService services will attempt to run on this port raising an error. To avoid this add `server.port=8081` to the resources/application.properties file. Ensure the port specified corresponds to port referenced by FirstServiceController.SECOND_SERVICE_URL. ***

Congrats, we just created a distributed service with OpenTelemetry!

## Manual Instrumentation using Handlers and Filters

In this section, we will implement the javax Servlet Filter interface to wrap all requests to FirstService and SecondService controllers in a span. 

We will also use the RestTemplate HTTP client to send requests from FirstService to SecondService. To propagate the trace in this request we will also implement the ClientHttpRequestInterceptor interface. This implementation is only required for projects that send outbound requests. In this example it is only required for FirstService. 

### Set up FirstService and SecondService

Using the earlier instructions [create two spring projects](#create-two-spring-projects) and add the required [dependencies and configurations](#setup-for-manual-instrumentation). 

### Instrumentation of Client Service: SecondService

Ensure the main method in SecondServiceApplication is defined. This will be the entry point to the SecondService project. This file should be created by the Spring Boot project initializer.

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

Add the REST controller below to your SecondService project. This controller will return a string when SecondServiceController.secondTracedMethod is called:

```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/time")
public class SecondServiceController {
   @Autowired
   private Tracer tracer;

   @GetMapping
   public String secondTracedMethod() {
      return "It's time to get a watch";
   }
}
```

#### Create Controller Filter

Add the class below to wrap all requests to the SecondServiceController in a span. This class will call the preHandle method before the REST controller is entered and the postHandle method after a response is created. 

The preHandle method starts a span for each request. This implementation is shown below:    

```java

@Component
public class ControllerFilter implements Filter {
  
  @Autowired
  Tracer tracer;
  
  private final Logger LOG = Logger.getLogger(ControllerFilter.class.getName());

  private final HttpTextFormat.Getter<HttpServletRequest> GETTER =
      new HttpTextFormat.Getter<HttpServletRequest>() {
        public String get(HttpServletRequest req, String key) {
          return req.getHeader(key);
        }
      };

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    LOG.info("start doFilter");
    
    HttpServletRequest req = (HttpServletRequest) request;
    Context context = OpenTelemetry.getPropagators().getHttpTextFormat()
        .extract(Context.current(), req, GETTER);
    Span currentSpan = createSpanWithParent(req, context);
      try (Scope scope = tracer.withSpan(currentSpan)) {
      currentSpan.addEvent("dofilter");
      chain.doFilter(req, response);
    }finally {
      LOG.info("end doFilter");
      currentSpan.end();
    }
    
  }
  
  private Span createSpanWithParent(HttpServletRequest request, Context context) {
    return tracer.spanBuilder(request.getRequestURI()).setSpanKind(Span.Kind.SERVER).startSpan();
  }
}
}

```

Now your SecondService application is complete. Create the FirstService application using the instructions below and then run your distributed service!

### Instrumentation of Receiving Service: FirstService

Ensure the main method in FirstServiceApplication is defined. This will be the entry point to the FirstService project. This file should be created by the Spring Boot project initializer.

```java
@SpringBootApplication
public class FirstServiceApplication {

   public static void main(String[] args) throws IOException {
      SpringApplication.run(FirstServiceApplication.class, args);
   }
}
```

Create a REST controller for FirstService. This controller will send a request to SecondService and then return the response to the client:

```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping(value = "/message")
public class FirstServiceController {
   @Autowired
   private Tracer tracer;
	
   @Autowired
   private RestTemplate restTemplate;

   @Autowired
   private HttpUtils httpUtils;

   private static final String SECOND_SERVICE_URL = "http://localhost:8081/time";

   @GetMapping
   public String firstTracedMethod() {

      ResponseEntity<String> response =
            restTemplate.exchange(SECOND_SERVICE_URL, HttpMethod.GET, null, String.class);
      String secondServiceTime = response.getBody();

      return "Second Service says: " + secondServiceTime;

   }
}
```

As seen in the setup of SecondService, implement the javax servlet filter interface to wrap requests to the SecondServiceController in a span. In effect, we will be taking a copy of the [ControllerFilter.java](#create-controller-filter) file defined in SecondService and adding it to FirstService.

#### Create Client Http Request Interceptor

Next, we will configure the ClientHttpRequestInterceptor to intercept all client HTTP requests made using RestTemplate.

To propagate the span context from FirstService to SecondService we must inject the trace parent and trace state into the outgoing request header. In section 1 this was done using the helper class HttpUtils. In this section, we will implement the ClientHttpRequestInterceptor interface and register this interceptor in our application. 

Include the two classes below to your FirstService project to add this functionality:


```java

import java.io.IOException;

import io.grpc.Context;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpRequest;

import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.context.propagation.HttpTextFormat;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;

@Component
public class RestTemplateInterceptor implements ClientHttpRequestInterceptor {

   @Autowired
   private Tracer tracer;

   private static final HttpTextFormat.Setter<HttpRequest> setter =
         new HttpTextFormat.Setter<HttpRequest>() {
            @Override
            public void set(HttpRequest carrier, String key, String value) {
               carrier.getHeaders().set(key, value);
            }
         };


   @Override
   public ClientHttpResponse intercept(HttpRequest request, byte[] body,
         ClientHttpRequestExecution execution) throws IOException {
      
      String spanName = request.getMethodValue() +  " " + request.getURI().toString();
      Span currentSpan = tracer.spanBuilder(spanName).setSpanKind(Span.Kind.CLIENT).startSpan();
      
      try (Scope scope = tracer.withSpan(currentSpan)) {
         OpenTelemetry.getPropagators().getHttpTextFormat().inject(Context.current(), request, setter);
         ClientHttpResponse response = execution.execute(request, body);
         LOG.info(String.format("Request sent from RestTemplateInterceptor"));

         return response;
      }finally {
         currentSpan.end();
      }
   }
}

```

```java
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestClientConfig {

   @Autowired
   RestTemplateHeaderModifierInterceptor restTemplateHeaderModifierInterceptor;

   @Bean
   public RestTemplate restTemplate() {
      RestTemplate restTemplate = new RestTemplate();

      restTemplate.getInterceptors().add(restTemplateHeaderModifierInterceptor);

      return restTemplate;
   }
}
```

### Create a distributed trace 

By default Spring Boot runs a Tomcat server on port 8080. This tutorial assumes FirstService runs on the default port (8080) and SecondService runs on port 8081. This is because we hard coded the SecondService end point in FirstServiceController.SECOND_SERVICE_URL. To run SecondServiceApplication on port 8081 include `server.port=8081` in the resources/application.properties file. 

Run both the FirstService and SecondService projects in terminal or using an IDE (ex. Eclipse). The end point for FirstService should be `http://localhost:8080/message` and `http://localhost:8081/time` for SecondService. Type both urls in a browser and ensure you receive a 200 response. 

To visualize this trace add a trace exporter to one or both of your applications. Instructions on how to setup LogExporter and Jaeger can be seen [above](#tracer-configuration). 

To create a sample trace enter `localhost:8080/message` in a browser. This trace should include a span for FirstService and a span for SecondService.
