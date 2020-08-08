# OpenTelemetry Spring Auto-Configuration

Auto-configures OpenTelemetry instrumentation for [spring-web](), [spring-webmvc](), and [spring-webflux](). Leverages Spring Aspect Oriented Programming, dependency injection, and bean post-processing to trace spring applications. To include use all features listed below use the [opentelemetry-spring-starter](../starters/spring-starter/README.md).

## Quickstart

### Add these dependencies to your project.

Replace `OPENTELEMETRY_VERSION` with the latest stable [release](https://mvnrepository.com/artifact/io.opentelemetry).
`Minimum version: 0.8.0`

For Maven add to your `pom.xml`:
```xml
<dependencies>
  <!-- opentelemetry -->
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-spring-boot-autoconfigure</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
  
  <dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-api</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>

   <!-- simple span exporter -->
   <!-- outputs spans to console -->
   <!-- provides opentelemetry-sdk artifact -->
   <dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporters-logging</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>

</dependencies>
```

For Gradle add to your dependencies:
```groovy
implementation 'io.opentelemetry.instrumentation:opentelemetry-spring-boot-autoconfigure:OPENTELEMETRY_VERSION'
implementation 'io.opentelemetry:opentelemetry-api:OPENTELEMETRY_VERSION'
implementation 'io.opentelemetry:opentelemetry-exporters-logging:OPENTELEMETRY_VERSION'
```

### Features

#### Dependencies

The following dependencies are optional but are required to use the corresponding features.

Replace `SPRING_VERSION` with the version of spring you're using.
`Minimum version: 3.1`

Replace `SPRING_WEBFLUX_VERSION` with the version of spring-webflux you're using.
`Minimum version: 5.0`

Replace `SLF4J_VERSION` with the version of slf4j you're using.

For Maven add to your `pom.xml`:
```xml
<dependencies>

  <!-- Used to autoconfigure spring-web -->
  <dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-web</artifactId>
    <version>SPRING_VERSION</version>
  </dependency>

  <!-- Used to autoconfigure spring-webmvc -->
  <dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-webmvc</artifactId>
    <version>SPRING_VERSION</version>
  </dependency>

  <!-- Used to autoconfigure spring-webflux -->
  <dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-webflux</artifactId>
    <version>SPRING_WEBFLUX_VERSION</version>
  </dependency>

  <!-- Used to enable instrumentation using @WithSpan  -->
  <dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-aop</artifactId>
    <version>SPRING_VERSION</version>
  </dependency>
  <dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-extension-auto-annotations</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>

  <!-- Slf4j log correlation support -->
  <dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-api</artifactId>
    <version>SLF4J_VERSION</version>
  </dependency>

</dependencies>
```

For Gradle add to your dependencies:
```groovy
//Used to autoconfigure spring-web
implementation "org.springframework:spring-web:SPRING_VERSION"

//Used to autoconfigure spring-webmvc
implementation "org.springframework:spring-webmvc:SPRING_VERSION"

//Used to autoconfigure spring-webflux
implementation "org.springframework:spring-webflux:SPRING_WEBFLUX_VERSION"

//Enables instrumentation using @WithSpan
implementation "org.springframework:spring-aop:SPRING_VERSION"
implementation "io.opentelemetry:opentelemetry-extension-auto-annotations:OPENTELEMETRY_VERSION"

//Slf4j log correlation support
implementation "org.sl4j:slf4j-api:SLF4J_VERSION"
```

#### OpenTelemetry Auto Configuration

#### Project Structure

<!-- TODO: Add diagram show casing all the features -->

#### Spring Web Auto Configuration

Provides autoconfigurations for the OpenTelemtry RestTemplate trace interceptor defined in [opentelemetry-spring-web-3.1](../spring-web-3.1/]. This autoconfiguration instruments all requests sent using Spring RestTemplate beans using conditional class loaders and a RestTemplate bean post processor. This feature is supported for spring web versions 3.1+ and can be disabled by adding `opentelemetry.trace.httpclients.enabled=False` to your `resources/applications.properties` file. Check out [opentelemetry-spring-web-3.1](../spring-web-3.1/] to learn more about the OpenTelemetry RestTemplateInterceptor.

#### Spring WebMvc Auto Configuration



#### Spring WebFlux Auto Configuration

Provides autoconfigurations for the OpenTelemtry WebClient ExchangeFilter defined in [opentelemetry-spring-webflux-5.0](../spring-webflux-5.0/). This autoconfiguration instruments all requests sent using Spring's WebClient and WebClient Builder bean using conditional class loaders and bean post processors. This feature is supported for spring webflux versions 5.0+ and can be disabled by adding `opentelemetry.trace.httpclients.enabled=False` to your `resources/applications.properties` file. Check out [opentelemetry-spring-webflux-5.0](../spring-webflux-5.0/) to learn more about the OpenTelemetry WebClientFilter.

#### Manual Instrumentation Support - @WithSpan

<!-- TODO: Merge PR with these changes
https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/902/files -->

##### Usage

```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.opentelemetry.extensions.auto.annotations.WithSpan;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;

/**
 * Test WithSpan
 *
 */
@Component
public class TracedClass {

    @Autowired
    // Tracer bean is provided by
    // io.opentelemetry.instrumentation.spring.autoconfigure.TracerAutoConfiguration.java
    Tracer tracer;

    @WithSpan
    public void tracedMethod() {
    }

    @WithSpan("span name")
    public void tracedMethodWithName() {
        Span currentSpan = tracer.getCurrentSpan();
        currentSpan.addEvent("ADD EVENT TO tracedMethodWithName SPAN");
        currentSpan.setAttribute("isTestAttribute", true);
    }
    
    @WithSpan(value="client span", kind=Span.Kind.CLIENT)
    public void tracedClientSpan() {
    }
}

```

##### Sample Trace

<!-- TODO: Add Image or LogSpanExporter Output -->


#### In Development - Slf4j Log Correlation

<!-- TODO: Merge PR with these changes -->

#### Spring Support

Auto-configuration is natively supported by Springboot applications. To enable these features in "vanilla" use `@EnableOpenTelemetryTracing` to complete a component scan of this package.

##### Usage

```java
import io.opentelemetry.instrumentation.spring.autoconfigure.EnableOpenTelemetryTracing
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableOpenTelemetryTracing
public class OpenTelemetryConfig {

}
```

#### Exporter Configurations

This package provides auto configurations for [OTLP](https://github.com/open-telemetry/opentelemetry-java/tree/master/exporters/otlp), [Jaeger](https://github.com/open-telemetry/opentelemetry-java/tree/master/exporters/jaeger), [Zipkin](https://github.com/open-telemetry/opentelemetry-java/tree/master/exporters/zipkin), and [Logging](https://github.com/open-telemetry/opentelemetry-java/tree/master/exporters/logging) Span Exporters.

If an exporter is present in the classpath during runtime and a spring bean of the exporter type is missing from the spring application context. An exporter bean is initialized and added to a simple span processor in the active tracer provider. Check out the implementation [here](/src/main/java/io/opentelemetry/instrumentation/spring/autoconfigure/TracerAutoConfiguration.java).


#### Configuration Properties

#### Enabling/Disabling Features

|Feature   				|Property   									|Default Value  |ConditionalOnClass
|---					|---											|---			|---
|spring-web  	 		|opentelemetry.trace.httpclients.enabled   		|true   		|RestTemplate
|spring-webmvc   		|opentelemetry.trace.httpclients.enabled   		|true   		|OncePerRequestFilter
|spring-webflux   		|opentelemetry.trace.httpclients.enabled 		|true   		|WebClient
|@WithSpan   			|opentelemetry.trace.aspects.enabled 	 		|true   		|WithSpan, Aspect
|Slf4j Log Correlation  |opentelemetry.trace.loggers.slf4j.enabled		|true   		|org.slf4j.MDC
|Otlp Exporter		    |opentelemetry.trace.exporters.otlp.enabled		|true   		|OtlpGrpcSpanExporter
|Jaeger Exporter		|opentelemetry.trace.exporters.jaeger.enabled	|true   		|JaegerGrpcSpanExporter
|Zipkin Exporter		|opentelemetry.trace.exporters.zipkin.enabled	|true   		|ZipkinSpanExporter
|Logging Exporter	    |opentelemetry.trace.exporters.logging.enabled	|true   		|LoggingSpanExporter

#### Exporter Properties

|Feature   				|Property   										|Default Value
|---					|---												|---
|Otlp Exporter  	 	|opentelemetry.trace.exporters.otlp.servicename 	|OtlpGrpcSpanExporter.DEFAULT_SERVICE_NAME
|				  		|opentelemetry.trace.exporters.otlp.endpoint		|OtlpGrpcSpanExporter.DEFAULT_ENDPOINT
|				   		|opentelemetry.trace.exporters.otlp.spantimeout		|OtlpGrpcSpanExporter.DEFAULT_DEADLINE_MS
|Jaeger Exporter  	 	|opentelemetry.trace.exporters.jaeger.servicename 	|JaegerGrpcSpanExporter.DEFAULT_SERVICE_NAME
|				  		|opentelemetry.trace.exporters.jaeger.endpoint		|JaegerGrpcSpanExporter.DEFAULT_ENDPOINT
|				   		|opentelemetry.trace.exporters.jaeger.spantimeout	|JaegerGrpcSpanExporter.DEFAULT_DEADLINE_MS
|Zipkin Exporter 		|opentelemetry.trace.exporters.jaeger.servicename	|ZipkinSpanExporter.DEFAULT_SERVICE_NAME
|				 		|opentelemetry.trace.exporters.jaeger.endpoint		|ZipkinSpanExporter.DEFAULT_ENDPOINT

#### Tracer Properties

|Feature   				|Property   										|Default Value
|---					|---												|---
|Tracer			  	 	|opentelemetry.trace.tracer.name 					|otel-spring-tracer
|				  	 	|opentelemetry.trace.tracer.samplerprobability 		|1.0


### Starter Guide

Check out the opentelemetry [quick start](https://github.com/open-telemetry/opentelemetry-java/blob/master/QUICKSTART.md) to learn more about OpenTelemetry instrumentation.
