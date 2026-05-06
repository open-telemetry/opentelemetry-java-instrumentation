# Library Instrumentation for Apache Thrift 0.13.0+

Provides OpenTelemetry instrumentation for [Apache Thrift](https://thrift.apache.org/).

## Quickstart

### Add the following dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest release](https://central.sonatype.com/artifact/io.opentelemetry.instrumentation/opentelemetry-thrift-0.13).

For Maven, add the following to your `pom.xml` dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-thrift-0.13</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add the following to your dependencies:

```groovy
implementation("io.opentelemetry.instrumentation:opentelemetry-thrift-0.13:OPENTELEMETRY_VERSION")
```

### Usage

The instrumentation library provides wrappers for thrift servers and clients that provide OpenTelemetry-based spans and context propagation.

```java
// For server-side, decorate processor with a tracing wrapper.
TProcessor configureServer(OpenTelemetry openTelemetry, TProcessor processor, String serviceName) {
  ThriftTelemetry thriftTelemetry = ThriftTelemetry.create(openTelemetry);
  return thriftTelemetry.wrapServerProcessor(processor, serviceName);
}

// For client-side, decorate protocol and client with a tracing wrappers.
TProtocol configureClient(OpenTelemetry openTelemetry, TProtocol protocol) {
  ThriftTelemetry thriftTelemetry = ThriftTelemetry.create(openTelemetry);
  return thriftTelemetry.wrapClientProtocol(protocol);
}

CustomService.Iface configureClient(CustomService.Client client) {
  ThriftTelemetry thriftTelemetry = ThriftTelemetry.create(openTelemetry);
  return thriftTelemetry.wrapClient(client, CustomService.Iface.class);
}

// For non-blocking client, decorate protocol factory and the async client with a tracing wrappers.
TProtocolFactory configureClient(OpenTelemetry openTelemetry, TProtocolFactory protocolFactory) {
  ThriftTelemetry thriftTelemetry = ThriftTelemetry.create(openTelemetry);
  return thriftTelemetry.wrapClientProtocolFactory(protocolFactory);
}

CustomService.AsyncIface configureClient(CustomService.AsyncClient asyncClient) {
  ThriftTelemetry thriftTelemetry = ThriftTelemetry.create(openTelemetry);
  return thriftTelemetry.wrapAsyncClient(asyncClient, CustomService.AsyncIface.class);
}
```
