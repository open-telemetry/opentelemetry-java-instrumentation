# Library Instrumentation for gRPC 1.6.0+

Provides OpenTelemetry instrumentation for [gRPC](https://grpc.io/).

## Quickstart

### Add the following dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest release](https://central.sonatype.com/artifact/io.opentelemetry.instrumentation/opentelemetry-grpc-1.6).

For Maven, add the following to your `pom.xml` dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-grpc-1.6</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add the following to your dependencies:

```groovy
implementation("io.opentelemetry.instrumentation:opentelemetry-grpc-1.6:OPENTELEMETRY_VERSION")
```

### Usage

The instrumentation library provides the implementation of `ClientInterceptor` and `ServerInterceptor` to provide OpenTelemetry-based spans and context propagation.

```java
// For client-side, supply the same target to the channel builder and interceptor.
ManagedChannel createChannel(OpenTelemetry openTelemetry, String target) {
  GrpcTelemetry grpcTelemetry = GrpcTelemetry.create(openTelemetry);
  return NettyChannelBuilder.forTarget(target)
      .intercept(grpcTelemetry.createClientInterceptor(target))
      .build();
}

// For server-side, configure the server builder.
void configureServer(OpenTelemetry openTelemetry, ServerBuilder<?> serverBuilder) {
  GrpcTelemetry grpcTelemetry = GrpcTelemetry.create(openTelemetry);
  grpcTelemetry.configureServerBuilder(serverBuilder);
}
```

`configureServerBuilder()` captures server spans for both registered and unregistered services.
`createServerInterceptor()` remains available for service-level registration, but cannot capture
requests to unregistered services.

Passing the channel target, such as `dns:///orders.example.com:8443`, lets the interceptor populate
`server.address` and `server.port` independently of an authority override. For custom name resolvers,
use an explicit `scheme://...` target. The no-argument `createClientInterceptor()` remains available
and uses channel authority instead.

#### Experimental automatic target capture

When the target is not available at the interceptor registration site, automatic capture is available
through an experimental API:

```java
import io.opentelemetry.instrumentation.grpc.v1_6.internal.Experimental;

Experimental.addClientInterceptor(grpcTelemetry, channelBuilder);
```

This depends on gRPC's internal target-aware interception API on gRPC 1.64.0 and newer. It falls back to
channel authority on older versions or unsupported builders. The experimental API may change or be
removed, and automatic target capture may stop working if the internal gRPC API changes.
