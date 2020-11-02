### P1 (e.g. cannot GA without these):
* End-to-end tests ([#298](https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/298#issuecomment-664162169))
  * OTLP, Jaeger and Zipkin ([#1541](https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/1541))
  * ✅Spring Boot and Wildfly
    * (Wildfly chosen due to common javaagent issues around jboss modules and jboss logging)
  * ✅Java 8, 11, and the latest Java version
* Benchmarking ([#595](https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/595))
  * Runtime overhead benchmark
  * Startup overhead benchmark
* All captured span attributes must either be from semantic attributes or be instrumentation-specific
  * TODO define convention for instrumentation-specific attributes, e.g. "elasticsearch.*"
* Basic configuration points
  * Add custom auto-instrumentation
  * ✅Ability to build "custom distro"
* Documentation
  * All configuration options
    * Standard OpenTelemetry SDK + Exporter configuration options
    * Auto-instrumentation configuration options (e.g. disable/enable, peer.service mapping)
  * For each instrumentation
    * Document any instrumentation-specific configuration
  * How to troubleshoot (start documenting common issues somewhere)
* Library (manual) instrumentations for a few libraries commonly used with Spring:
  Spring WebMVC, Spring WebFlux, Spring RestTemplate, JDBC
  * (this requirement is to ensure that we have a good path forward for supporting both auto and manual instrumentation)

### P2
* Contributor experience (tag "contributor experience" plus tag "cleanup" plus tag "sporadic test failure")
  * New contributor documentation
    * How to write new instrumentation (auto, library, tests)
    * How to understand and fix muzzle issues
    * How to submit your first PR (CLA, check for CI failures, note about sporadic failures)
  * Faster builds
  * Fewer sporadic CI failures
  * Publish a debug jar without the classdata obfuscation

### P3
* Auto-collected metrics
  * System / JVM metrics (https://github.com/open-telemetry/opentelemetry-specification/issues/651)
  * Request metrics (https://github.com/open-telemetry/opentelemetry-specification/issues/522, https://github.com/open-telemetry/opentelemetry-specification/pull/657)
* Library (manual) instrumentations for more libraries commonly used with Spring
  * Spring Kafka, Spring AMQP, Reactor, java.util.concurrent
* Library (manual) instrumentations for libraries commonly used with Android
  * OkHttp, gRPC
* Document the basic configuration points
  * How to write your own auto-instrumentation
    * (much of this can be shared with contributor documentation below)
  * How to build your own "custom distro"
* Complete instrumentation documentation, with commitment to keeping this up-to-date going forward
  * Document all spans that it captures
    * Span names
    * Span attributes (including explanation of any non-semantic attributes)
    * Events
  * Document any other effects (e.g. updating SERVER span name with route)

### Instrumentation prioritization

When it comes to prioritizing work, sometimes it's helpful to know the relative importance of a
particular instrumentation, e.g. making improvements in Spring WebFlux instrumentation would
generally take priority over making improvement in Grizzly instrumentation.

This is only intended as a guide for prioritizing work.

### P1

* Apache AsyncHttpClient
* Apache HttpClient
* Cassandra Driver
* gRPC
* HttpURLConnection
* JAX-RS
* JDBC
* Jedis
* JMS
* Kafka
* Lettuce
* MongoDB Drivers
* Netty
* OkHttp
* RabbitMQ
* Reactor
* Servlet
* Spring Scheduling
* Spring Web MVC
* Spring Webflux

### P2

* All others
