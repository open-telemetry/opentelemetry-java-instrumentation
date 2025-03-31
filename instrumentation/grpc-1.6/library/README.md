# Library Instrumentation for gRPC 1.6.0+

Provides OpenTelemetry instrumentation for [gRPC](https://grpc.io/).

## Quickstart

### Add the following dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest release](https://search.maven.org/search?q=g:io.opentelemetry.instrumentation%20AND%20a:opentelemetry-grpc-1.6).

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
// For client-side, attach the interceptor to your channel builder.
void configureClientInterceptor(OpenTelemetry openTelemetry, NettyChannelBuilder nettyChannelBuilder) {
  GrpcTelemetry grpcTelemetry = GrpcTelemetry.create(openTelemetry);
  nettyChannelBuilder.intercept(grpcTelemetry.newClientInterceptor());
}

// For server-side, attatch the interceptor to your service.
ServerServiceDefinition configureServerInterceptor(OpenTelemetry openTelemetry, ServerServiceDefinition serviceDefinition) {
  GrpcTelemetry grpcTelemetry = GrpcTelemetry.create(openTelemetry);
  return ServerInterceptors.intercept(serviceDefinition, grpcTelemetry.newServerInterceptor());
}
```
