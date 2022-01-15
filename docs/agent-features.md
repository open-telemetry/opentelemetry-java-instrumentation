# OpenTelemetry Java Agent Features

This lists out some of the features specific to java agents that OpenTelemetry Auto Instrumentation
provides.

- Bundled exporters
  - [OTLP](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/protocol/otlp.md)
  - Jaeger gRPC
  - Logging
  - Zipkin
- Bundled propagators
  - [W3C TraceContext / Baggage](https://www.w3.org/TR/trace-context/)
  - All Java [trace propagator extensions](https://github.com/open-telemetry/opentelemetry-java/tree/main/extensions/trace-propagators)
- Environment variable configuration as per [spec](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/sdk-environment-variables.md)
  - Additional support for system properties for same variables by transforming UPPER_UNDERSCORE -> lower.dot
  - Ability to disable individual instrumentation, or only enable certain ones.
- Ability to load a custom exporter via an external JAR library
- Isolation from application
  - Separate Agent classloader with almost all agent-specific classes
    - OpenTelemetry SDK initialized in Agent classloader
  - Shading of instrumentation libraries when used in agent
  - API bridge for application usage of API to access the Agent classloader's SDK
    - API bridge not applied if user brings incompatible API version, preventing linkage errors (similar to safety mechanism below)
- [Safety mechanisms](./safety-mechanisms.md) to prevent application linkage errors
  - Collect all references from instrumentation to library and only apply instrumentation if they exist in application
  - Verify above at compile time
  - Instrumentation tests that run the java agent in a near-production configuration
  - Ability to run tests against a fixed version and the latest version of dependencies
  - Docker-based smoke tests to verify agent behavior across JVM runtimes, Java application servers
- Ability to create custom distributions, agents with different components / configuration
  - Can set different defaults for properties
  - Can customize tracer configuration programmatically
  - Can provide custom exporter, propagator, sampler
  - Can hook into bytebuddy to customize bytecode manipulation
- Noteworthy instrumentation
  - Log injection of IDs (logback, log4j2, log4j)
  - Automatic context propagation across `Executor`s
  - Ability to instrument methods in the application if user adds `@WithSpan` annotation
