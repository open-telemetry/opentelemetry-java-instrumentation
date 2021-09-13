## Introduction

Extensions add new features and capabilities to the agent without having to create a separate distribution (for examples and ideas, see [Use cases for extensions](#use-cases-for-extensions)). 

The contents in this folder demonstrate how to create an extension for the OpenTelemetry Java instrumentation agent, with examples for every extension point. 

> Read both the source code and the Gradle build script, as they contain documentation that explains the purpose of all the major components.

## Build and add extensions

To build this extension project, run `./gradlew build`. You can find the resulting jar file in `build/libs/`. 

To add the extension to the instrumentation agent:

1. Copy the jar file to a host that is running an application to which you've attached the OpenTelemetry Java instrumentation.
2. Modify the startup command to add the full path to the extension file. For example:

     ```bash
     java -javaagent:path/to/opentelemetry-javaagent.jar \
          -Dotel.javaagent.extensions=build/libs/opentelemetry-java-instrumentation-extension-demo-1.0-all.jar
          -jar myapp.jar
     ```
## Embed extensions in the OpenTelemetry Agent

To simplify deployment, you can embed extensions into the OpenTelemetry Java Agent to produce a single jar file. With an integrated extension, you no longer need the `-Dotel.javaagent.extensions` command line option.

For more information, see the `extendedAgent` task in [build.gradle](build.gradle).
## Extensions examples

* Custom `IdGenerator`: [DemoIdGenerator](src/main/java/com/example/javaagent/DemoIdGenerator.java)
* Custom `TextMapPropagator`: [DemoPropagator](src/main/java/com/example/javaagent/DemoPropagator.java)
* New default configuration: [DemoPropertySource](src/main/java/com/example/javaagent/DemoPropertySource.java)
* Custom `Sampler`: [DemoSampler](src/main/java/com/example/javaagent/DemoSampler.java)
* Custom `SpanProcessor`: [DemoSpanProcessor](src/main/java/com/example/javaagent/DemoSpanProcessor.java)
* Custom `SpanExporter`: [DemoSpanExporter](src/main/java/com/example/javaagent/DemoSpanExporter.java)
* Additional instrumentation: [DemoServlet3InstrumentationModule](src/main/java/com/example/javaagent/instrumentation/DemoServlet3InstrumentationModule.java)

## Sample use cases

Extensions are designed to override or customize the instrumentation provided by the upstream agent without having to create a new OpenTelemetry distribution or alter the agent code in any way.

Consider an instrumented database client that creates a span per database call and extracts data from the database connection to provide span attributes. The following are sample use cases for that scenario that can be solved by using extensions.

### "I don't want this span at all"

Create an extension to disable selected instrumentation by providing new default settings.

### "I want to edit some attributes that don't depend on any db connection instance"

Create an extension that provide a custom `SpanProcessor`.

### "I want to edit some attributes and their values depend on a specific db connection instance"

Create an extension with new instrumentation which injects its own advice into the same method as the original one. You can use the `order` method to ensure it runs after the original instrumentation and augment the current span with new information.

For example, see [DemoServlet3InstrumentationModule](src/main/java/com/example/javaagent/instrumentation/DemoServlet3InstrumentationModule.java).

### "I want to remove some attributes"

Create an extension with a custom exporter or use the attribute filtering functionality in the OpenTelemetry Collector.

### "I don't like the OTel spans. I want to modify them and their lifecycle"

Create an extension that disables existing instrumentation and replace it with new one that injects `Advice` into the same (or a better) method as the original instrumentation. You can write your `Advice` for this and use the existing `Tracer` directly or extend it. As you have your own `Advice`, you can control which `Tracer` you use.
