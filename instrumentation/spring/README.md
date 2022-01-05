# OpenTelemetry Instrumentation: Spring and Spring Boot
<!-- ReadMe is in progress -->
<!-- TO DO: Add sections for starter guide -->

This package streamlines the manual instrumentation process of OpenTelemetry for [Spring](https://spring.io/projects/spring-framework) and [Spring Boot](https://spring.io/projects/spring-boot) applications. It will enable you to add traces to requests and database calls with minimal changes to application code. This package will not fully automate your OpenTelemetry instrumentation, instead, it will provide you with better tools to instrument your own code.

The [first section](#manual-instrumentation-with-java-sdk) will walk you through span creation and propagation using the OpenTelemetry Java API and [Spring's RestTemplate Http Web Client](https://spring.io/guides/gs/consuming-rest/). This approach will use the "vanilla" OpenTelemetry API to make explicit tracing calls within an application's controller.

The [second section](#manual-instrumentation-using-handlers-and-filters)  will build on the first. It will walk you through implementing spring-web handler and filter interfaces to create traces with minimal changes to existing application code. Using the OpenTelemetry API, this approach involves copy and pasting files and a significant amount of manual configurations.

The [third section](#auto-instrumentation-using-spring-starters) with build on the first two sections. We will use spring auto-configurations and instrumentation tools packaged in OpenTelemetry [Spring Starters](starters) to streamline the set up of OpenTelemetry using Spring. With these tools you will be able to setup distributed tracing with little to no changes to existing configurations and easily customize traces with minor additions to application code.

In this guide we will be using a running example. In section one and two, we will create two spring web services using Spring Boot. We will then trace requests between these services using two different approaches. Finally, in section three we will explore tools documented in [opentelemetry-spring-boot-autoconfigure](/spring-boot-autoconfigure/README.md#features) which can improve this process.

# Settings 

| System property | Type | Default | Description |
|---|---|---|---|
| `otel.instrumentation.spring-integration.global-channel-interceptor-patterns` | List | `*` | An array of Spring channel name patterns that will be intercepted. See [Spring Integration docs](https://docs.spring.io/spring-integration/reference/html/channel.html#global-channel-configuration-interceptors) for more details. |
| `otel.instrumentation.spring-integration.producer.enabled` | Boolean | `false` | Create producer spans when  messages are sent to an output channel. Enable when you're using a messaging library that doesn't have its own instrumentation for generating producer spans. Note that the detection of output channels only works for [Spring Cloud Stream](https://spring.io/projects/spring-cloud-stream) `DirectWithAttributesChannel`.  |
| `otel.instrumentation.spring-webflux.experimental-span-attributes` | Boolean | `false` | Enable the capture of experimental span attributes for Spring WebFlux version 5.0. |
| `otel.instrumentation.spring-webmvc.experimental-span-attributes` | Boolean | `false` | Enable the capture of experimental span attributes for Spring Web MVC 3.1. |

# Manual Instrumentation Guide

## Create two Spring Projects

Using the [spring project initializer](https://start.spring.io/), we will create two spring projects.  Name one project `MainService` and the other `TimeService`. In this example `MainService` will be a client of `TimeService` and they will be dealing with time. Make sure to select maven, Spring Boot 2.3, Java, and add the spring-web dependency. After downloading the two projects include the OpenTelemetry dependencies and configuration listed below.

## Setup for Manual Instrumentation

Add the dependencies below to enable OpenTelemetry in `MainService` and `TimeService`. The Jaeger and LoggingExporter packages are recommended for exporting traces but are not required. As of May 2020, Jaeger, Zipkin, OTLP, and Logging exporters are supported by opentelemetry-java. Feel free to use whatever exporter you are most comfortable with.

Replace `OPENTELEMETRY_VERSION` with the latest stable [release](https://search.maven.org/search?q=g:io.opentelemetry).
 - Minimum version: `1.1.0`
 
### Maven

#### OpenTelemetry
```xml
<dependency>
   <groupId>io.opentelemetry</groupId>
   <artifactId>opentelemetry-api</artifactId>
   <version>OPENTELEMETRY_VERSION</version>
</dependency>
<dependency>
   <groupId>io.opentelemetry</groupId>
   <artifactId>opentelemetry-sdk</artifactId>
   <version>OPENTELEMETRY_VERSION</version>
</dependency>
```

#### LoggingSpanExporter
```xml
<dependency>
   <groupId>io.opentelemetry</groupId>
   <artifactId>opentelemetry-exporters-logging</artifactId>
   <version>OPENTELEMETRY_VERSION</version>
</dependency>
```

#### Jaeger Exporter
```xml
<dependency>
   <groupId>io.opentelemetry</groupId>
   <artifactId>opentelemetry-exporters-jaeger</artifactId>
   <version>OPENTELEMETRY_VERSION</version>
</dependency>
<dependency>
   <groupId>io.grpc</groupId>
   <artifactId>grpc-netty</artifactId>
   <version>1.30.2</version>
</dependency>
```

### Gradle

#### OpenTelemetry
```gradle
implementation("io.opentelemetry:opentelemetry-api:OPENTELEMETRY_VERSION")
implementation("io.opentelemetry:opentelemetry-sdk:OPENTELEMETRY_VERSION")
```

#### LoggingExporter
```gradle
implementation("io.opentelemetry:opentelemetry-exporters-logging:OPENTELEMETRY_VERSION")
```

#### Jaeger Exporter
```gradle
implementation("io.opentelemetry:opentelemetry-exporters-jaeger:OPENTELEMETRY_VERSION")
compile "io.grpc:grpc-netty:1.30.2"
```

### Tracer Configuration

To enable tracing in your OpenTelemetry project configure a Tracer Bean. This bean will be auto wired to controllers to create and propagate spans. This can be seen in the `Tracer otelTracer()` method below. If you plan to use a trace exporter remember to also include it in this configuration class. In section 3 we will use an annotation to set up this configuration.

A sample OpenTelemetry configuration using LoggingExporter is shown below:

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.grpc.ManagedChannelBuilder;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporters.jaeger.JaegerGrpcSpanExporter;
import io.opentelemetry.exporters.logging.*;

@Configuration
public class OtelConfig {
   private static final String tracerName = "fooTracer";
   @Bean
   public Tracer otelTracer() {
      Tracer tracer = OpenTelemetry.getGlobalTracer(tracerName);

      SpanProcessor logProcessor = SimpleSpanProcessor.newBuilder(new LoggingSpanExporter()).build();
      OpenTelemetrySdk.getTracerManagement().addSpanProcessor(logProcessor);

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
OpenTelemetrySdk.getTracerManagement().addSpanProcessor(jaegerProcessor);
```

### Project Background

Here we will create REST controllers for `MainService` and `TimeService`.
`MainService` will send a GET request to `TimeService` to retrieve the current time. After this request is resolved, `MainService` then will append a message to time and return a string to the client.

## Manual Instrumentation with Java SDK

### Add OpenTelemetry to MainService and TimeService

Required dependencies and configurations for MainService and TimeService projects can be found [here](#setup-for-manual-instrumentation).

### Instrumentation of MainService

1. Ensure OpenTelemetry dependencies are included
2. Ensure an OpenTelemetry Tracer is configured

3. Ensure a Spring Boot main class was created by the Spring initializer

```java
@SpringBootApplication
public class MainServiceApplication {

   public static void main(String[] args) {
      SpringApplication.run(MainServiceApplication.class, args);
   }
}
```

4. Create a REST controller for MainService
5. Create a span to wrap MainServiceController.message()

```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.opentelemetry.context.Scope;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;

import HttpUtils;

@RestController
@RequestMapping(value = "/message")
public class MainServiceController {
   private static int requestCount = 1;
   private static final String TIME_SERVICE_URL = "http://localhost:8081/time";

   @Autowired
   private Tracer tracer;

   @Autowired
   private HttpUtils httpUtils;

   @GetMapping
   public String message() {
      Span span = tracer.spanBuilder("message").startSpan();

      try (Scope scope = tracer.withSpan(span)) {
         span.addEvent("Controller Entered");
         span.setAttribute("timeservicecontroller.request.count", requestCount++);
         return "Time Service says: " + httpUtils.callEndpoint(TIME_SERVICE_URL);
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

6. Configure `HttpUtils.callEndpoint` to inject span context into request. This is key to propagate the trace to the TimeService

HttpUtils is a helper class that injects the current span context into outgoing requests. This involves adding the tracer id and the trace-state to a request header. For this example, we used `RestTemplate` to send requests from `MainService` to `TimeService`. A similar approach can be used with popular Java Web Clients such as [okhttp](https://square.github.io/okhttp/) and [apache http client](https://www.tutorialspoint.com/apache_httpclient/apache_httpclient_quick_guide.htm). The key to this implementation is to override the put method in `TextMapPropagator.Setter<?>` to handle your request format. `TextMapPropagator.inject` will use this setter to set `traceparent` and `tracestate` headers in your requests. These values will be used to propagate your span context to external services.

```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import io.opentelemetry.context.Context;

import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.Tracer;

@Component
public class HttpUtils {

  private static final TextMapPropagator.Setter<HttpHeaders> setter = new TextMapPropagator.Setter<HttpHeaders>() {
    @Override
    public void set(HttpHeaders headers, String key, String value) {
      headers.set(key, value);
    }
  };

  @Autowired
  private Tracer tracer;

  private final TextMapPropagator<SpanContext> textFormat;

  public HttpUtils(Tracer tracer) {
    textFormat = tracer.getTextMapPropagator();
  }

  public String callEndpoint(String url) {
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
### Instrumentation of TimeService

1. Ensure OpenTelemetry dependencies are included
2. Ensure an OpenTelemetry Tracer is configured
3. Ensure a Spring Boot main class was created by the Spring initializer

```java
import java.io.IOException;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TimeServiceApplication {

   public static void main(String[] args) {
      SpringApplication.run(TimeServiceApplication.class, args);
   }
}
```

4. Create a REST controller for TimeService
5. Start a span to wrap TimeServiceController.time()

```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.opentelemetry.context.Scope;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;

@RestController
@RequestMapping(value = "/time")
public class TimeServiceController {
   @Autowired
   private Tracer tracer;

   @GetMapping
   public String time() {
      Span span = tracer.spanBuilder("time").startSpan();

      try (Scope scope = tracer.withSpan(span)) {
         span.addEvent("TimeServiceController Entered");
         span.setAttribute("what.am.i", "Tu es une legume");
         return "It's time to get a watch";
      } finally {
         span.end();
      }
   }
}
```

### Run MainService and TimeService

***To view your distributed traces ensure either LogExporter or Jaeger is configured in the OtelConfig.java file***

To view traces on the Jaeger UI, deploy a Jaeger Exporter on localhost by running the command in terminal:

`docker run --rm -it --network=host jaegertracing/all-in-one`

After running Jaeger locally, navigate to the url below. Make sure to refresh the UI to view the exported traces from the two web services:

`http://localhost:16686`

Run MainService and TimeService from command line or using an IDE. The end point of interest for MainService is `http://localhost:8080/message` and  `http://localhost:8081/time` for TimeService. Entering `localhost:8080/message` in a browser should call MainService and then TimeService, creating a trace.

***Note: The default port for the Apache Tomcat is 8080. On localhost both MainService and TimeService services will attempt to run on this port raising an error. To avoid this add `server.port=8081` to the resources/application.properties file. Ensure the port specified corresponds to port referenced by MainServiceController.TIME_SERVICE_URL. ***

Congrats, we just created a distributed service with OpenTelemetry!

## Manual Instrumentation using Handlers and Filters

In this section, we will implement the javax Servlet Filter interface to wrap all requests to MainService and TimeService controllers in a span.

We will also use the RestTemplate HTTP client to send requests from MainService to TimeService. To propagate the trace in this request we will also implement the ClientHttpRequestInterceptor interface. This implementation is only required for projects that send outbound requests. In this example it is only required for MainService.

### Set up MainService and TimeService

Using the earlier instructions [create two spring projects](#create-two-spring-projects) and add the required [dependencies and configurations](#setup-for-manual-instrumentation).

### Instrumentation of TimeService

Ensure the main method in TimeServiceApplication is defined. This will be the entry point to the TimeService project. This file should be created by the Spring Boot project initializer.

```java
import java.io.IOException;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TimeServiceApplication {

   public static void main(String[] args) {
      SpringApplication.run(TimeServiceApplication.class, args);
   }
}
```

Add the REST controller below to your TimeService project. This controller will return a string when TimeServiceController.time is called:

```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/time")
public class TimeServiceController {
   @Autowired
   private Tracer tracer;

   @GetMapping
   public String time() {
      return "It's time to get a watch";
   }
}
```

#### Create Controller Filter

Add the class below to wrap all requests to the TimeServiceController in a span. This class will call the preHandle method before the REST controller is entered and the postHandle method after a response is created.

The preHandle method starts a span for each request. This implementation is shown below:

```java

@Component
public class ControllerFilter implements Filter {
  private static final Logger logger = Logger.getLogger(ControllerFilter.class.getName());

  @Autowired
  Tracer tracer;

  private final TextMapPropagator.Getter<HttpServletRequest> GETTER =
      new TextMapPropagator.Getter<HttpServletRequest>() {
        public String get(HttpServletRequest req, String key) {
          return req.getHeader(key);
        }
      };

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) {
    LOG.info("start doFilter");

    HttpServletRequest req = (HttpServletRequest) request;
    Span currentSpan;
    try (Scope scope = tracer.withSpan(currentSpan)) {
      Context context = OpenTelemetry.getPropagators().getTextMapPropagator()
          .extract(Context.current(), req, GETTER);
      currentSpan = createSpanWithParent(req, context);
      currentSpan.addEvent("dofilter");
      chain.doFilter(req, response);
    } finally {
      currentSpan.end();
    }

    LOG.info("end doFilter");
  }

  private Span createSpanWithParent(HttpServletRequest request, Context context) {
    return tracer.spanBuilder(request.getRequestURI()).setSpanKind(SpanKind.SERVER).startSpan();
  }
}

```

Now your TimeService application is complete. Create the MainService application using the instructions below and then run your distributed service!

### Instrumentation of MainService

Ensure the main method in MainServiceApplication is defined. This will be the entry point to the MainService project. This file should be created by the Spring Boot project initializer.

```java
@SpringBootApplication
public class MainServiceApplication {

   public static void main(String[] args) {
      SpringApplication.run(MainServiceApplication.class, args);
   }
}
```

Create a REST controller for MainService. This controller will send a request to TimeService and then return the response to the client:

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
public class MainServiceController {
   private static final String TIME_SERVICE_URL = "http://localhost:8081/time";

   @Autowired
   private Tracer tracer;

   @Autowired
   private RestTemplate restTemplate;

   @Autowired
   private HttpUtils httpUtils;

   @GetMapping
   public String message() {

      ResponseEntity<String> response =
            restTemplate.exchange(TIME_SERVICE_URL, HttpMethod.GET, null, String.class);
      String currentTime = response.getBody();

      return "Time Service: " + currentTime;

   }
}
```

As seen in the setup of TimeService, implement the javax servlet filter interface to wrap requests to the TimeServiceController in a span. In effect, we will be taking a copy of the [ControllerFilter.java](#create-controller-filter) file defined in TimeService and adding it to MainService.

#### Create Client Http Request Interceptor

Next, we will configure the ClientHttpRequestInterceptor to intercept all client HTTP requests made using RestTemplate.

To propagate the span context from MainService to TimeService we must inject the trace parent and trace state into the outgoing request header. In section 1 this was done using the helper class HttpUtils. In this section, we will implement the ClientHttpRequestInterceptor interface and register this interceptor in our application.

Include the two classes below to your MainService project to add this functionality:

```java

import java.io.IOException;

import io.opentelemetry.context.Context;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpRequest;

import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;

@Component
public class RestTemplateInterceptor implements ClientHttpRequestInterceptor {

  @Autowired
  private Tracer tracer;

  private static final TextMapPropagator.Setter<HttpRequest> setter =
      new TextMapPropagator.Setter<HttpRequest>() {
        @Override
        public void set(HttpRequest carrier, String key, String value) {
          carrier.getHeaders().set(key, value);
        }
      };


  @Override
  public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                      ClientHttpRequestExecution execution) {

    String spanName = request.getMethodValue() + " " + request.getURI().toString();
    Span currentSpan = tracer.spanBuilder(spanName).setSpanKind(SpanKind.CLIENT).startSpan();

    try (Scope scope = tracer.withSpan(currentSpan)) {
      OpenTelemetry.getPropagators().getTextMapPropagator().inject(Context.current(), request, setter);
      ClientHttpResponse response = execution.execute(request, body);
      LOG.info("Request sent from RestTemplateInterceptor");

      return response;
    } finally {
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

By default Spring Boot runs a Tomcat server on port 8080. This tutorial assumes MainService runs on the default port (8080) and TimeService runs on port 8081. This is because we hard coded the TimeService end point in MainServiceController.TIME_SERVICE_URL. To run TimeServiceApplication on port 8081 include `server.port=8081` in the resources/application.properties file.

Run both the MainService and TimeService projects in terminal or using an IDE (ex. Eclipse). The end point for MainService should be `http://localhost:8080/message` and `http://localhost:8081/time` for TimeService. Type both urls in a browser and ensure you receive a 200 response.

To visualize this trace add a trace exporter to one or both of your applications. Instructions on how to setup LogExporter and Jaeger can be seen [above](#tracer-configuration).

To create a sample trace enter `localhost:8080/message` in a browser. This trace should include a span for MainService and a span for TimeService.



## Auto Instrumentation using Spring Starters

In this tutorial we will create two SpringBoot applications (MainService and TimeService). We will use [opentelemetry-spring-starter](starters/spring-starter) to enable distributed tracing using OpenTelemetry and export spans using the default LoggingSpanExporter. We will also use the [opentelemetry-zipkin-exporter-starter](starters/zipkin-exporter-starter) to export traces to Zipkin.

### OpenTelemetry Spring Starter Dependencies

Add the following dependencies to your build file.

Replace `OPENTELEMETRY_VERSION` with the latest stable [release](https://search.maven.org/search?q=g:io.opentelemetry).
 - Minimum version: `1.1.0`

#### Maven
```xml
<dependency>
   <groupId>io.opentelemetry.instrumentation</groupId>
   <artifactId>opentelemetry-spring-starter</artifactId>
   <version>OPENTELEMETRY_VERSION</version>
</dependency>
```

#### Gradle
```gradle
implementation("io.opentelemetry.instrumentation:opentelemetry-spring-starter:OPENTELEMETRY_VERSION")
```

### Create two Spring Projects

Using the [spring project initializer](https://start.spring.io/), we will create two spring projects.  Name one project `MainService` and the other `TimeService`. Make sure to select maven, Spring Boot 2.3, Java, and add the spring-web dependency. After downloading the two projects include the OpenTelemetry dependencies listed above.

### Main Service Application

Configure the main class in your `MainService` project to match the file below. In this example `MainService` will be a client of `TimeService`. The RestController and RestTemplate Bean initialized in the file below will be auto-instrumented by the opentelemetry spring starter.

```java
import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
public class MainServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(MainServiceApplication.class, args);
  }

  @RestController
  @RequestMapping(value = "/message")
  public static class MainServiceController {
    private static final String TIME_SERVICE_URL = "http://localhost:8080/time";

    @Autowired
    private RestTemplate restTemplate;

    @GetMapping
    public String message() {
      return restTemplate.exchange(TIME_SERVICE_URL, HttpMethod.GET, null, String.class).getBody();
    }

    @Bean
    public RestTemplate restTemplate() {
      return new RestTemplate();
    }
  }
}
```

#### Application Configurations

The following tracer configurations can be used to customize your instrumentation. Add the following values to your project's resource/application.properties file:

```properties

## TimeService will run on port 8080
## Setting the server port of MainService to 8081 will prevent conflicts
server.port=8081

## Default configurations
#otel.traces.sampler.probability=1
#otel.springboot.web.enabled=true
#otel.springboot.httpclients.enabled=true
#otel.springboot.aspects.enabled=true

```

Check out [OpenTelemetry Spring Boot AutoConfigure](spring-boot-autoconfigure/README.md) to learn more.



### TimeService

Configure the main class in your `Time Service` project to match the file below. Here we use the Tracer bean provided by the OpenTelemetry starter to create an internal span and set some additional events and attributes. 

```java 

import java.io.IOException;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import io.opentelemetry.context.Scope;
import io.opentelemetry.extension.annotations.WithSpan;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;

@SpringBootApplication
public class TimeServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(TimeServiceApplication.class, args);
  }

  @RestController
  @RequestMapping(value = "/time")
  public class TimeServiceController {
     @Autowired
     private Tracer tracer;

     @GetMapping
     public String time() {
        withSpanMethod();

        Span span = tracer.spanBuilder("time").startSpan();
        try (Scope scope = tracer.withSpan(span)) {
           span.addEvent("TimeServiceController Entered");
           span.setAttribute("what.am.i", "Tu es une legume");
           return "It's time to get a watch";
        } finally {
           span.end();
        }
     }
     
     @WithSpan(kind=SpanKind.SERVER)
     public void withSpanMethod() {}
  }
}
```


### Generating Trace - LoggingSpanExporter

To generate a trace, run MainServiceApplication and TimeServiceApplication, and then send a request to `localhost:8080/message`. Shown below is the output of the default span exporter - (LoggingSpanExporter)[https://github.com/open-telemetry/opentelemetry-java/tree/main/exporters/logging].

#### MainService

```java
SpanWrapper{
delegate=RecordEventsReadableSpan{traceId=TraceId{traceId=52d6edec17bbf842cf5032ebce2043f8}, spanId=SpanId{spanId=15b72a8e85c842c5}, 
parentSpanId=SpanId{spanId=57f0106dd1121b54}, name=HTTP GET, kind=CLIENT, attributes={net.peer.name=AttributeValueString{stringValue=localhost},
http.status_code=AttributeValueLong{longValue=200}, net.peer.port=AttributeValueLong{longValue=8080}, 
http.url=AttributeValueString{stringValue=http://localhost:8080/time}, http.method=AttributeValueString{stringValue=GET}}, 
status=Status{canonicalCode=OK, description=null}, totalRecordedEvents=0, totalRecordedLinks=0, startEpochNanos=1598409410457933181, 
endEpochNanos=1598409410925420912}, resolvedLinks=[], resolvedEvents=[], attributes={net.peer.name=AttributeValueString{stringValue=localhost},
http.status_code=AttributeValueLong{longValue=200}, net.peer.port=AttributeValueLong{longValue=8080}, 
http.url=AttributeValueString{stringValue=http://localhost:8080/time}, http.method=AttributeValueString{stringValue=GET}}, totalAttributeCount=5, 
totalRecordedEvents=0, status=Status{canonicalCode=OK, description=null}, name=HTTP GET, endEpochNanos=1598409410925420912, hasEnded=true
}

SpanWrapper{
delegate=RecordEventsReadableSpan{traceId=TraceId{traceId=52d6edec17bbf842cf5032ebce2043f8}, spanId=SpanId{spanId=57f0106dd1121b54}, 
parentSpanId=SpanId{spanId=0000000000000000}, name=WebMVCTracingFilter.doFilterInteral, kind=SERVER, attributes={http.status_code=AttributeValueLong{longValue=200}, 
sampling.probability=AttributeValueDouble{doubleValue=1.0}, net.peer.port=AttributeValueLong{longValue=57578}, 
http.user_agent=AttributeValueString{stringValue=PostmanRuntime/7.26.2}, http.flavor=AttributeValueString{stringValue=1.1},
http.url=AttributeValueString{stringValue=/message}, net.peer.ip=AttributeValueString{stringValue=0:0:0:0:0:0:0:1}, 
http.method=AttributeValueString{stringValue=GET}, http.client_ip=AttributeValueString{stringValue=0:0:0:0:0:0:0:1}}, 
status=Status{canonicalCode=OK, description=null}, totalRecordedEvents=0, totalRecordedLinks=0, startEpochNanos=1598409410399317331, endEpochNanos=1598409411045782693},
resolvedLinks=[], resolvedEvents=[], attributes={http.status_code=AttributeValueLong{longValue=200}, sampling.probability=AttributeValueDouble{doubleValue=1.0},
net.peer.port=AttributeValueLong{longValue=57578}, http.user_agent=AttributeValueString{stringValue=PostmanRuntime/7.26.2}, 
http.flavor=AttributeValueString{stringValue=1.1}, http.url=AttributeValueString{stringValue=/message},
net.peer.ip=AttributeValueString{stringValue=0:0:0:0:0:0:0:1}, http.method=AttributeValueString{stringValue=GET}, 
http.client_ip=AttributeValueString{stringValue=0:0:0:0:0:0:0:1}}, totalAttributeCount=9, totalRecordedEvents=0, 
status=Status{canonicalCode=OK, description=null}, name=WebMVCTracingFilter.doFilterInteral, endEpochNanos=1598409411045782693, hasEnded=true
}
```

#### TimeService

```java
SpanWrapper{
delegate=RecordEventsReadableSpan{traceId=TraceId{traceId=52d6edec17bbf842cf5032ebce2043f8}, 
spanId=SpanId{spanId=f2d824704be8ab10}, parentSpanId=SpanId{spanId=b4ae77c523215f9d}, 
name=time, kind=INTERNAL, attributes={what.am.i=AttributeValueString{stringValue=Tu es une legume}}, status=null, 
totalRecordedEvents=1,totalRecordedLinks=0, startEpochNanos=1598409410738665807, endEpochNanos=1598409410740607921}, resolvedLinks=[], 
resolvedEvents=[RawTimedEvent{name=TimeServiceController Entered, attributes={}, epochNanos=1598409410738760924, totalAttributeCount=0}], 
attributes={what.am.i=AttributeValueString{stringValue=Tu es une legume}}, totalAttributeCount=1, totalRecordedEvents=1, 
status=Status{canonicalCode=OK, description=null}, name=time, endEpochNanos=1598409410740607921, hasEnded=true
}

SpanWrapper{
delegate=RecordEventsReadableSpan{traceId=TraceId{traceId=52d6edec17bbf842cf5032ebce2043f8}, spanId=SpanId{spanId=b4ae77c523215f9d}, 
parentSpanId=SpanId{spanId=15b72a8e85c842c5}, name=WebMVCTracingFilter.doFilterInteral, kind=SERVER, 
attributes={http.status_code=AttributeValueLong{longValue=200}, net.peer.port=AttributeValueLong{longValue=40174}, 
http.user_agent=AttributeValueString{stringValue=Java/11.0.8}, http.flavor=AttributeValueString{stringValue=1.1},
http.url=AttributeValueString{stringValue=/time}, net.peer.ip=AttributeValueString{stringValue=127.0.0.1}, 
http.method=AttributeValueString{stringValue=GET}, http.client_ip=AttributeValueString{stringValue=127.0.0.1}}, 
status=Status{canonicalCode=OK, description=null}, totalRecordedEvents=0, totalRecordedLinks=0, startEpochNanos=1598409410680549805, 
endEpochNanos=1598409410921631068}, resolvedLinks=[], resolvedEvents=[], attributes={http.status_code=AttributeValueLong{longValue=200},
net.peer.port=AttributeValueLong{longValue=40174}, http.user_agent=AttributeValueString{stringValue=Java/11.0.8}, 
http.flavor=AttributeValueString{stringValue=1.1}, http.url=AttributeValueString{stringValue=/time},
net.peer.ip=AttributeValueString{stringValue=127.0.0.1}, http.method=AttributeValueString{stringValue=GET}, 
http.client_ip=AttributeValueString{stringValue=127.0.0.1}}, totalAttributeCount=8, totalRecordedEvents=0, 
status=Status{canonicalCode=OK, description=null}, name=WebMVCTracingFilter.doFilterInteral, endEpochNanos=1598409410921631068, hasEnded=true
}

```


### Exporter Starters 

To configure OpenTelemetry tracing with the OTLP, Zipkin, or Jaeger span exporters replace the OpenTelemetry Spring Starter dependency with one of the artifacts listed below:

#### Maven
```xml

<!-- opentelemetry starter with zipkin -->
<dependency>
	<groupId>io.opentelemetry.instrumentation</groupId>
	<artifactId>opentelemetry-zipkin-exporter-starter</artifactId>
	<version>OPENTELEMETRY_VERSION</version>
</dependency> 

<!-- opentelemetry starter with jaeger -->
<dependency>
	<groupId>io.opentelemetry.instrumentation</groupId>
	<artifactId>opentelemetry-jaeger-exporter-starter</artifactId>
	<version>OPENTELEMETRY_VERSION</version>
</dependency> 

<!-- opentelemetry starter with otlp -->
<dependency>
	<groupId>io.opentelemetry.instrumentation</groupId>
	<artifactId>opentelemetry-otlp-exporter-starter</artifactId>
	<version>OPENTELEMETRY_VERSION</version>
</dependency> 
```

#### Gradle
```gradle

//opentelemetry starter with zipkin configurations
implementation("io.opentelemetry.instrumentation:opentelemetry-zipkin-exporter-starter:OPENTELEMETRY_VERSION")

//opentelemetry starter with jaeger configurations
implementation("io.opentelemetry.instrumentation:opentelemetry-jaeger-exporter-starter:OPENTELEMETRY_VERSION")

//opentelemetry starter with otlp configurations
implementation("io.opentelemetry.instrumentation:opentelemetry-otlp-exporter-starter:OPENTELEMETRY_VERSION")
```

#### Exporter Configuration Properties

Add the following configurations to overwrite the default exporter values listed below.

```
## Default tracer configurations
#otel.traces.sampler.probability=1

## Default exporter configurations
#otel.exporter.otlp.endpoint=localhost:4317
#otel.exporter.otlp.timeout=10s
#otel.exporter.jaeger.endpoint=localhost:14250
#otel.exporter.jaeger.timeout=10s
#otel.exporter.zipkin.endpoint=http://localhost:9411/api/v2/spans
```

### Sample Trace Zipkin

To generate a trace using the zipkin exporter follow the steps below: 
 1. Replace `opentelemetry-spring-starter` with `opentelemetry-zipkin-starter` in your pom or gradle build file
 2. Use the Zipkin [quick starter](https://zipkin.io/pages/quickstart) to download and run the zipkin executable jar 
    - Ensure the zipkin endpoint matches the default value listed in your application properties
 3. Run `MainServiceApplication.java` and `TimeServiceApplication.java`
 4. Use your favorite browser to send a request to `http://localhost:8080/message`
 5. Navigate to `http://localhost:9411` to see your trace


Shown below is the sample trace generated by `MainService` and `TimeService` using the opentelemetry-zipkin-exporter-starter. 

```json
[
   {
      "traceId":"52d6edec17bbf842cf5032ebce2043f8",
      "parentId":"b4ae77c523215f9d",
      "id":"f2d824704be8ab10",
      "name":"time",
      "timestamp":1598409410738665,
      "duration":1942,
      "localEndpoint":{
         "serviceName":"time_service_zipkin_trace",
         "ipv4":"192.XXX.X.XXX"
      },
      "annotations":[
         {
            "timestamp":1598409410738760,
            "value":"TimeServiceController Entered"
         }
      ],
      "tags":{
         "what.am.i":"Tu es une legume"
      }
   },
   {
      "traceId":"52d6edec17bbf842cf5032ebce2043f8",
      "parentId":"15b72a8e85c842c5",
      "id":"b4ae77c523215f9d",
      "kind":"SERVER",
      "name":"webmvctracingfilter.dofilterinteral",
      "timestamp":1598409410680549,
      "duration":241082,
      "localEndpoint":{
         "serviceName":"time_service_zipkin_trace",
         "ipv4":"192.XXX.X.XXX"
      },
      "tags":{
         "http.client_ip":"127.0.0.1",
         "http.flavor":"1.1",
         "http.method":"GET",
         "http.status_code":"200",
         "http.url":"/time",
         "http.user_agent":"Java/11.0.8",
         "net.peer.ip":"127.0.0.1",
         "net.peer.port":"40174"
      }
   },
   {
      "traceId":"52d6edec17bbf842cf5032ebce2043f8",
      "parentId":"57f0106dd1121b54",
      "id":"15b72a8e85c842c5",
      "kind":"CLIENT",
      "name":"http get",
      "timestamp":1598409410457933,
      "duration":467487,
      "localEndpoint":{
         "serviceName":"main_service_zipkin_trace",
         "ipv4":"192.XXX.X.XXX"
      },
      "tags":{
         "http.method":"GET",
         "http.status_code":"200",
         "http.url":"http://localhost:8080/time",
         "net.peer.name":"localhost",
         "net.peer.port":"8080"
      }
   },
   {
      "traceId":"52d6edec17bbf842cf5032ebce2043f8",
      "id":"57f0106dd1121b54",
      "kind":"SERVER",
      "name":"webmvctracingfilter.dofilterinteral",
      "timestamp":1598409410399317,
      "duration":646465,
      "localEndpoint":{
         "serviceName":"main_service_zipkin_trace",
         "ipv4":"192.XXX.X.XXX"
      },
      "tags":{
         "http.client_ip":"0:0:0:0:0:0:0:1",
         "http.flavor":"1.1",
         "http.method":"GET",
         "http.status_code":"200",
         "http.url":"/message",
         "http.user_agent":"PostmanRuntime/7.26.2",
         "net.peer.ip":"0:0:0:0:0:0:0:1",
         "net.peer.port":"57578",
         "sampling.probability":"1.0"
      }
   }
]

```
