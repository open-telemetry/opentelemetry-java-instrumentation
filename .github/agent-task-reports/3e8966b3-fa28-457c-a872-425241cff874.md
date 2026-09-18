## Decision

Keep the current title and body. They accurately describe the user-visible attribute behavior, put the recommended library API near the top, explain the gRPC version fallback, and call out the public API deprecations without repeating implementation details from the diff.

```json
{
  "decision": "keep",
  "evidence": {
    "body_basis": "The complete diff captures configured gRPC channel targets for client server.address and server.port attributes, adds target parsing for DNS, Unix domain sockets, IPv4, IPv6, xDS, unknown schemes, and authority fallback, introduces GrpcTelemetry.addClientInterceptor(ManagedChannelBuilder) with target capture on gRPC 1.64 and newer, preserves createClientInterceptor() for interceptor-only integrations, updates javaagent and Armeria integration paths, and deprecates GrpcRequest.getLogicalHost() and getLogicalPort() in favor of getServerAddress() and getServerPort(). The current title and body cover the user-facing behavior, recommended API, compatibility boundary, and deprecations concisely.",
    "changed_files": [
      "CHANGELOG.md",
      "instrumentation/armeria/armeria-grpc-1.14/javaagent/src/main/java/io/opentelemetry/javaagent/instrumentation/armeria/grpc/v1_14/ArmeriaGrpcClientBuilderInstrumentation.java",
      "instrumentation/grpc-1.6/javaagent/src/main/java/io/opentelemetry/javaagent/instrumentation/grpc/v1_6/GrpcClientBuilderBuildInstrumentation.java",
      "instrumentation/grpc-1.6/javaagent/src/main/java/io/opentelemetry/javaagent/instrumentation/grpc/v1_6/GrpcSingletons.java",
      "instrumentation/grpc-1.6/javaagent/src/test/java/io/opentelemetry/javaagent/instrumentation/grpc/v1_6/GrpcTest.java",
      "instrumentation/grpc-1.6/library/README.md",
      "instrumentation/grpc-1.6/library/src/main/java/io/opentelemetry/instrumentation/grpc/v1_6/GrpcRequest.java",
      "instrumentation/grpc-1.6/library/src/main/java/io/opentelemetry/instrumentation/grpc/v1_6/GrpcServerNetworkAttributesGetter.java",
      "instrumentation/grpc-1.6/library/src/main/java/io/opentelemetry/instrumentation/grpc/v1_6/GrpcTelemetry.java",
      "instrumentation/grpc-1.6/library/src/main/java/io/opentelemetry/instrumentation/grpc/v1_6/TracingClientInterceptor.java",
      "instrumentation/grpc-1.6/library/src/main/java/io/opentelemetry/instrumentation/grpc/v1_6/TracingServerInterceptor.java",
      "instrumentation/grpc-1.6/library/src/main/java/io/opentelemetry/instrumentation/grpc/v1_6/internal/GrpcClientNetworkAttributesGetter.java",
      "instrumentation/grpc-1.6/library/src/main/java/io/opentelemetry/instrumentation/grpc/v1_6/internal/GrpcTargetParser.java",
      "instrumentation/grpc-1.6/library/src/main/java/io/opentelemetry/instrumentation/grpc/v1_6/internal/Internal.java",
      "instrumentation/grpc-1.6/library/src/main/java/io/opentelemetry/instrumentation/grpc/v1_6/internal/ParsedTarget.java",
      "instrumentation/grpc-1.6/library/src/test/java/io/opentelemetry/instrumentation/grpc/v1_6/GrpcAttributesExtractorTest.java",
      "instrumentation/grpc-1.6/library/src/test/java/io/opentelemetry/instrumentation/grpc/v1_6/GrpcStreamingTest.java",
      "instrumentation/grpc-1.6/library/src/test/java/io/opentelemetry/instrumentation/grpc/v1_6/GrpcTest.java",
      "instrumentation/grpc-1.6/library/src/test/java/io/opentelemetry/instrumentation/grpc/v1_6/internal/GrpcTargetParserTest.java",
      "instrumentation/grpc-1.6/testing/src/main/java/io/opentelemetry/instrumentation/grpc/v1_6/AbstractGrpcTest.java"
    ]
  },
  "proposal": {
    "title": "Populate gRPC `server.address` and `server.port` from channel targets",
    "body": "gRPC client spans now populate `server.address` and `server.port` from configured channel targets instead of relying only on channel authority. Target parsing covers DNS, Unix domain socket, IPv4, IPv6, and xDS addresses. Direct-address channels fall back to authority.\n\n### Library API\n\nUse `addClientInterceptor` when configuring a `ManagedChannelBuilder`:\n\n```java\nGrpcTelemetry telemetry = GrpcTelemetry.create(openTelemetry);\ntelemetry.addClientInterceptor(channelBuilder);\n```\n\nOn gRPC 1.64 and newer, this method captures the builder target. Older versions fall back to channel authority. `createClientInterceptor()` remains supported for integrations that accept only a `ClientInterceptor`, but it cannot capture the builder target.\n\n### Compatibility\n\n`GrpcRequest.getLogicalHost()` and `getLogicalPort()` are deprecated. Use `getServerAddress()` and `getServerPort()` instead."
  },
  "request": {
    "type": "pull_request_description"
  },
  "repository": "open-telemetry/opentelemetry-java-instrumentation",
  "pull_request": 16161,
  "head": {
    "repository": "trask/opentelemetry-java-instrumentation",
    "branch": "grpc-server-address",
    "sha": "02ad2ba216cdc6ef2f3f3768f7c5bc700ed62eac"
  },
  "base": {
    "repository": "open-telemetry/opentelemetry-java-instrumentation",
    "branch": "main"
  },
  "title": "Populate gRPC `server.address` and `server.port` from channel targets",
  "body": "gRPC client spans now populate `server.address` and `server.port` from configured channel targets instead of relying only on channel authority. Target parsing covers DNS, Unix domain socket, IPv4, IPv6, and xDS addresses. Direct-address channels fall back to authority.\n\n### Library API\n\nUse `addClientInterceptor` when configuring a `ManagedChannelBuilder`:\n\n```java\nGrpcTelemetry telemetry = GrpcTelemetry.create(openTelemetry);\ntelemetry.addClientInterceptor(channelBuilder);\n```\n\nOn gRPC 1.64 and newer, this method captures the builder target. Older versions fall back to channel authority. `createClientInterceptor()` remains supported for integrations that accept only a `ClientInterceptor`, but it cannot capture the builder target.\n\n### Compatibility\n\n`GrpcRequest.getLogicalHost()` and `getLogicalPort()` are deprecated. Use `getServerAddress()` and `getServerPort()` instead."
}
```
