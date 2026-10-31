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

The instrumentation library instruments Thrift servers and clients to provide OpenTelemetry-based
spans and context propagation.

```java
// For server-side, decorate the generated processor with a tracing wrapper.
TProcessor configureServer(OpenTelemetry openTelemetry, CustomService.Iface handler) {
  ThriftTelemetry thriftTelemetry = ThriftTelemetry.create(openTelemetry);
  return thriftTelemetry.instrument(
      new CustomService.Processor<>(handler), CustomService.class.getName());
}

// When using AsyncProcessor, also instrument the input and output protocol factories.
void configureServer(OpenTelemetry openTelemetry, TNonblockingServer.Args tnbArgs) {
  ThriftTelemetry thriftTelemetry = ThriftTelemetry.create(openTelemetry);
  // TBinaryProtocol.Factory is the default
  tnbArgs.inputProtocolFactory(thriftTelemetry.instrumentServerInProtocolFactory(new TBinaryProtocol.Factory()));
  tnbArgs.outputProtocolFactory(thriftTelemetry.instrumentServerOutProtocolFactory(new TBinaryProtocol.Factory()));
}

// For client-side, instrument the protocol and client.
TProtocol configureClient(OpenTelemetry openTelemetry, TProtocol protocol) {
  ThriftTelemetry thriftTelemetry = ThriftTelemetry.create(openTelemetry);
  return thriftTelemetry.instrument(protocol);
}

CustomService.Iface configureClient(OpenTelemetry openTelemetry, CustomService.Client client) {
  ThriftTelemetry thriftTelemetry = ThriftTelemetry.create(openTelemetry);
  return thriftTelemetry.instrument(client, CustomService.Iface.class);
}

// For non-blocking clients, instrument the protocol factory and async client.
TProtocolFactory configureClient(OpenTelemetry openTelemetry, TProtocolFactory protocolFactory) {
  ThriftTelemetry thriftTelemetry = ThriftTelemetry.create(openTelemetry);
  return thriftTelemetry.instrumentClientProtocolFactory(protocolFactory);
}

CustomService.AsyncIface configureClient(OpenTelemetry openTelemetry, CustomService.AsyncClient asyncClient) {
  ThriftTelemetry thriftTelemetry = ThriftTelemetry.create(openTelemetry);
  return thriftTelemetry.instrument(asyncClient, CustomService.AsyncIface.class);
}
```

The `rpcMethodPrefix` passed to `instrument` is combined with the method name as
`prefix/method` for server span names and the `rpc.method` attribute on server spans and duration
metrics. Use the generated service class name, as above, to match client telemetry.
