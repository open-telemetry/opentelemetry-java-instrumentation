# PR description analysis

Keep the current title and body. They accurately summarize target-based `server.address` and `server.port` extraction, put the new library API usage near the top, explain compatibility behavior, and call out the deprecated `GrpcRequest` accessors. A fresh draft would not be meaningfully clearer or more complete.

The complete diff at head `02ad2ba216cdc6ef2f3f3768f7c5bc700ed62eac` changes 20 files. It adds target capture to library, javaagent, and Armeria client instrumentation; parses DNS, Unix, IP, xDS, unknown, and direct-address targets; falls back to authority; updates network attribute getters; documents and tests `addClientInterceptor`; and deprecates the logical host and port getters.

```json
{
  "decision": "keep",
  "evidence": {
    "body_basis": "The complete diff adds gRPC channel-target capture and parsing for client server.address and server.port attributes, authority fallback, the ManagedChannelBuilder-based addClientInterceptor API, compatibility handling for older gRPC versions and createClientInterceptor users, javaagent and Armeria integration updates, deprecated GrpcRequest logical host and port accessors, documentation, changelog entries, and focused tests.",
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
    "id": "4f8be110-c1b5-4daf-981a-1f52a34ce988",
    "type": "pull_request_description"
  },
  "repository": {
    "owner": "open-telemetry",
    "name": "opentelemetry-java-instrumentation"
  },
  "pull_request": {
    "number": 16161,
    "url": "https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/16161"
  },
  "head": {
    "repository": "trask/opentelemetry-java-instrumentation",
    "branch": "grpc-server-address",
    "sha": "02ad2ba216cdc6ef2f3f3768f7c5bc700ed62eac"
  },
  "base": {
    "repository": "open-telemetry/opentelemetry-java-instrumentation",
    "branch": "main"
  },
  "title": {
    "current": "Populate gRPC `server.address` and `server.port` from channel targets"
  },
  "body": {
    "current": "gRPC client spans now populate `server.address` and `server.port` from configured channel targets instead of relying only on channel authority. Target parsing covers DNS, Unix domain socket, IPv4, IPv6, and xDS addresses. Direct-address channels fall back to authority.\n\n### Library API\n\nUse `addClientInterceptor` when configuring a `ManagedChannelBuilder`:\n\n```java\nGrpcTelemetry telemetry = GrpcTelemetry.create(openTelemetry);\ntelemetry.addClientInterceptor(channelBuilder);\n```\n\nOn gRPC 1.64 and newer, this method captures the builder target. Older versions fall back to channel authority. `createClientInterceptor()` remains supported for integrations that accept only a `ClientInterceptor`, but it cannot capture the builder target.\n\n### Compatibility\n\n`GrpcRequest.getLogicalHost()` and `getLogicalPort()` are deprecated. Use `getServerAddress()` and `getServerPort()` instead."
  }
}
```
