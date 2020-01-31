# OpenTelemetry Automatic Java Agent

## Notice - Work in Progress!
*This project is still in the early phases of development and is not yet stable.* You are welcome to experiment with it, 
but we strongly discourage any production use!

## Introduction
This project uses a technique known as bytecode instrumentation to add tracing to a Java program.
Simply put, we provide an agent that can be attached to any Java program and dynamically adds code to enable tracing. 

## Using the agent
As mentioned above, this project is in a very early phase of development and not yet stable. 
However, you can try it on your Java program by following these instructions.

### Prerequisites
* Java 8 for building (Java 7 to Java 13 are supported at runtime)
* Gradle 6.0
* Jaeger 1.16
* Git (reasonably current version)

### Downloading and building
```git clone https://github.com/open-telemetry/opentelemetry-auto-instr-java.git```

```gradle assemble```

### Running 
The instrumentation agent is enabled using the -javaagent flag to the JVM. Configuration parameters are passed 
as Java system properties (-D flags) or as environment variables. This is an example of how to run
the agent on a java program:

```java -javaagent:$OTHOME/java-agent/build/libs/java-agent-0.1.0-SNAPSHOT.jar -Dota.exporter=jaeger -Dota.jaeger.host=localhost -Dota.jaeger.port=14250 -Dota.jaeger.service=shopping -jar myprogram.jar```

#### Configuration parameters (subject to change!)
System property | Environment variable | Purpose
--- | --- | ---
ota.exporter | OTA_EXPORTER | The name of the exporter. Currently only supports 'jaeger' for Jager over gRPC
ota.jaeger.host | OTA_JAEGER_HOST | The Jaeger host to connect to. Currently only gRPC is supported.
ota.jaeger.port | OTA_JAEGER_PORT | The port to connect to on the Jaeger host. Currently only gRPC is supported
ota.jaeger.service | OTA_JAEGER_SERVICE | The service name of this JVM instance. This is used as a label in Jaeger to distinguish between JVM instances in a multi-service environment.

These parameter names are very likely to change over time, so please check back here when trying out a new version!
