# OpenTelemetry Auto-Instrumentation for Java

## Notice - Work in Progress!
*This project is still in the early phases of development and is not yet stable.* You are welcome to experiment with it, 
but we strongly discourage any production use!

## Introduction
This project uses a technique known as bytecode instrumentation to add tracing to a Java application.
Simply put, we provide a Java agent that can be attached to any Java 7+ application and dynamically adds code to enable tracing. 

## Using the agent
As mentioned above, this project is in a very early phase of development and not yet stable. 
However, you can try it on your Java application by following these instructions.

### Download and run

Download the [latest release](https://github.com/open-telemetry/opentelemetry-auto-instr-java/releases).

The instrumentation agent is enabled using the -javaagent flag to the JVM. Configuration parameters are passed 
as Java system properties (-D flags) or as environment variables. This is an example:

```
java -javaagent:/path/to/opentelemetry-auto-<version>.jar \
     -Dota.exporter.jar=exporter-adapters/dummy-exporter-adapter/build/libs/dummy-exporter-adapter-0.1.2-SNAPSHOT.jar \
     -Dota.jaeger.host=localhost \
     -Dota.jaeger.port=14250 \
     -Dota.jaeger.service=shopping \
     -jar myapp.jar
```

### Configuration parameters (subject to change!)
System property | Environment variable | Purpose
--- | --- | ---
ota.exporter.jar | OTA_EXPORTER_JAR | The path to an exporter JAR
ota.service | OTA_SERVICE | The service name of this JVM instance. This is used as a label in Jaeger to distinguish between JVM instances in a multi-service environment.

### Available exporters
Currently two exporters are available and bundled with this project. They area available under the ```exporter-adapters``` directory. 

#### Dummy Exporter
The dummy exporter simply prints the name of the span along with its attributes to stdout. It is used manly
for testing and debugging. It takes a single configuration parameter.

System property | Environment variable | Purpose
--- | --- | ---
ota.exporter.dummy.prefix | OTA_EXPORTER_DUMMY_PREFIX | A string that is printed in front of the span name and attributes.

#### Jaeger exporter
A simple wrapper for the Jaeger exporter of opentelemetry-java. It currently only supports gRPC as its communications protocol.

System property | Environment variable | Purpose
--- | --- | ---
ota.exporter.jaeger.host | OTA_JAEGER_HOST | The Jaeger host to connect to. Currently only gRPC is supported.
ota.exporter.jaeger.port | OTA_JAEGER_PORT | The port to connect to on the Jaeger host. Currently only gRPC is supported

These parameter names are very likely to change over time, so please check back here when trying out a new version!

Please report any bugs or unexpected behavior you may find.

#### Vendor-specific exporters
Exporters from observability and Application Performance Management vendors are currently under development. We will compile a list of vendor-specific exporters as they become available.

## Building from source

Build using Java 8:

```gradle assemble```

and then you can find the java agent artifact at `java-agent/build/lib/opentelemetry-auto-<version>.jar`.
