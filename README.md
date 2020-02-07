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
     -Dota.exporter=jaeger \
     -Dota.jaeger.host=localhost \
     -Dota.jaeger.port=14250 \
     -Dota.jaeger.service=shopping \
     -jar myapp.jar
```

### Configuration parameters (subject to change!)
System property | Environment variable | Purpose
--- | --- | ---
ota.exporter | OTA_EXPORTER | The name of the exporter. Currently only supports 'jaeger' for Jager over gRPC
ota.service | OTA_SERVICE | The service name of this JVM instance. This is used as a label in Jaeger to distinguish between JVM instances in a multi-service environment.
ota.jaeger.host | OTA_JAEGER_HOST | The Jaeger host to connect to. Currently only gRPC is supported.
ota.jaeger.port | OTA_JAEGER_PORT | The port to connect to on the Jaeger host. Currently only gRPC is supported

These parameter names are very likely to change over time, so please check back here when trying out a new version!

Please report any bugs or unexpected behavior you may find.

## Building from source

Build using Java 8:

```gradle assemble```

and then you can find the java agent artifact at `java-agent/build/lib/opentelemetry-auto-<version>.jar`.
