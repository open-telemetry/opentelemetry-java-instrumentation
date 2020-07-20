P0 (e.g. cannot GA without these):
* End-to-end tests
  * OTLP, Jaeger and Zipkin
  * Tomcat, Wildfly, Spring Boot
  * All Java LTS versions + latest Java version if non-LTS
* Benchmarking
  * Startup overhead benchmark
  * Runtime overhead benchmark
* Implement all required semantic attributes
  * Non-required semantic attributes are nice to have, but not required for GA
* Basic configuration points
  * Add custom auto-instrumentation
  * Ability to build "custom distro"
* Documentation (tag "documentation" minus tag "contributor experience")
  * All configuration options
    * Standard OpenTelemetry SDK + Exporter configuration options
    * Auto-instrumentation configuration options (e.g. disable/enable, peer.service mapping)
  * For each instrumentation
    * Document all spans that it captures
      * Span names
      * Span attributes (including explanation of any non-semantic attributes)
      * Events
    * Document any instrumentation-specific configuration
    * Document any other effects (e.g. updating SERVER span name with route)
  * Document the basic configuration points (above)
    * How to write your own auto-instrumentation
      * (much of this can be shared with contributor documentation below)
    * How to build your own "custom distro"
  * How to troubleshoot (start documenting common issues somewhere)
* Library (manual) instrumentations corresponding to at least 20 auto-instrumentations, with shared code and tests

P1:
* Contributor experience (tag "contributor experience" plus tag "cleanup" plus tag "sporadic test failure")
  * New contributor documentation
    * How to write new instrumentation (auto, library, tests)
    * How to understand and fix muzzle issues
    * How to submit your first PR (CLA, check for CI failures, note about sporadic failures)
  * Faster builds
  * Fewer sporadic CI failures
  * Publish a debug jar without the classdata obfuscation

P3:
* Auto-collected metrics
  * System / JVM metrics (https://github.com/open-telemetry/opentelemetry-specification/issues/651)
  * Request metrics (https://github.com/open-telemetry/opentelemetry-specification/issues/522, https://github.com/open-telemetry/opentelemetry-specification/pull/657)

P4:
* Library (manual) instrumentation corresponding to all auto-instrumentation

P5:
* Non-required semantic attributes
