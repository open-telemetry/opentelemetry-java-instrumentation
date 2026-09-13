# Scope of this repository

## Instrumentation maintained here

This repository focuses on Java agent and standalone library instrumentation that implements
[OpenTelemetry semantic conventions](https://opentelemetry.io/docs/specs/semconv/), including:

- HTTP client and server telemetry
- RPC client and server telemetry
- Messaging telemetry
- Database client and connection pool telemetry
- JVM runtime and system metrics
- GraphQL server telemetry
- FaaS server telemetry
- GenAI client telemetry
- Resource detection for:
  - Containers
  - Hosts
  - Operating systems
  - Processes and the Java runtime

The repository also maintains instrumentation for:

- Context propagation
- Scheduled job execution
- Collection of logging framework events as OpenTelemetry logs
- Injection of trace context into logging framework context
- Spring Boot starters

Proposals outside these areas are considered based on their maintenance cost, signal quality,
cardinality, overlap with existing instrumentation, and value to users.
