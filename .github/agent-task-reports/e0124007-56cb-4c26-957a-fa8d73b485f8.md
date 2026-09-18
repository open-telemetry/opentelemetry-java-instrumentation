## Decision

Keep the current title and body. They accurately describe the new channel-target capture, the supported target forms and authority fallback, the recommended library API, gRPC version compatibility, and the deprecated `GrpcRequest` accessors. The API example is near the top and the body is concise and easy to scan.

```json
{
  "decision": "keep",
  "evidence": {
    "body_basis": "The 20-file diff adds gRPC target parsing, captures ManagedChannelBuilder targets for client spans, populates server.address and server.port with authority fallback, introduces and documents GrpcTelemetry.addClientInterceptor, updates javaagent and Armeria integration paths, deprecates the logical host and port accessors, and adds parser and target-capture coverage. The current title and body cover each user-facing change without unnecessary implementation detail.",
    "changed_files": [
      "CHANGELOG.md",
      "instrumentation/armeria/armeria-grpc-1.14/javaagent/src/main/java/io/opentelemetry/javaagent/instrumentation/armeria/grpc/v1_14/ArmeriaGrpcClientBuilderInstrumentation.java",
      "instrumentation/grpc-1.6/javaagent/src/main/java/io/opentelemetry/javaagent/instrumentation/grpc/v1_6/GrpcClientBuilderBuildInstrumentation.java",
      "instrumentation/grpc-1.6/javaagent/src/main/java/io/opentelemetry/javaagent/instrumentation/grpc/v1_6/GrpcSingletons.java",
      "instrumentation/grpc-1.6/javaagent/src/test/java/io/opentelemetry/javaagent/instrumentation/grpc/v1_6/GrpcTest.java",
      "instrumentation/grpc-1.6/library/src/main/java/io/opentelemetry/instrumentation/grpc/v1_6/TracingServerInterceptor.java",
      "instrumentation/grpc-1.6/library/src/main/java/io/opentelemetry/instrumentation/grpc/v1_6/internal/GrpcClientNetworkAttributesGetter.java",
      "instrumentation/grpc-1.6/library/src/main/java/io/opentelemetry/instrumentation/grpc/v1_6/internal/GrpcTargetParser.java",
      "instrumentation/grpc-1.6/library/src/main/java/io/opentelemetry/instrumentation/grpc/v1_6/internal/Internal.java",
      "instrumentation/grpc-1.6/library/src/main/java/io/opentelemetry/instrumentation/grpc/v1_6/internal/ParsedTarget.java",
      "instrumentation/grpc-1.6/library/README.md",
      "instrumentation/grpc-1.6/library/src/main/java/io/opentelemetry/instrumentation/grpc/v1_6/GrpcRequest.java",
      "instrumentation/grpc-1.6/library/src/main/java/io/opentelemetry/instrumentation/grpc/v1_6/GrpcServerNetworkAttributesGetter.java",
      "instrumentation/grpc-1.6/library/src/main/java/io/opentelemetry/instrumentation/grpc/v1_6/GrpcTelemetry.java",
      "instrumentation/grpc-1.6/library/src/main/java/io/opentelemetry/instrumentation/grpc/v1_6/TracingClientInterceptor.java",
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
}
```
