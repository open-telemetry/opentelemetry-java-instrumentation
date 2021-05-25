## Introduction

This repository demonstrates how to create an extension archive to use with `otel.javaagent.experimental.extensions`
configuration option of the OpenTelemetry Java instrumentation agent.

For every extension point provided by OpenTelemetry Java instrumentation, this repository contains an example of
its usage.

Please carefully read both the source code and Gradle build script file `build.gradle`.
They contain a lot of documentation and comments explaining the purpose of all major pieces.

## How to use extension archive

When you build this project by running `./gradlew build` you will get a jar file in
`build/libs/opentelemetry-java-instrumentation-extension-demo-1.0-all.jar`.
Copy this jar file to a machine running the application that you are monitoring with
OpenTelemetry Java instrumentation agent.

Assuming that your command line looks similar to this:
```
java -javaagent:path/to/opentelemetry-javaagent-all.jar \
     -jar myapp.jar
```
change it to this:
```
java -javaagent:path/to/opentelemetry-javaagent-all.jar \
     -Dotel.javaagent.experimental.extensions=path/to/extension.jar
     -jar myapp.jar
```
specifying the full path and the correct name of your extensions jar.

## Extensions examples

* [DemoIdGenerator](src/main/java/com/example/javaagent/DemoIdGenerator.java) - custom `IdGenerator`
* [DemoPropagator](src/main/java/com/example/javaagent/DemoPropagator.java) - custom `TextMapPropagator`
* [DemoPropertySource](src/main/java/com/example/javaagent/DemoPropertySource.java) - default configuration
* [DemoSampler](src/main/java/com/example/javaagent/DemoSampler.java) - custom `Sampler`
* [DemoSpanProcessor](src/main/java/com/example/javaagent/DemoSpanProcessor.java) - custom `SpanProcessor`
* [DemoSpanExporter](src/main/java/com/example/javaagent/DemoSpanExporter.java) - custom `SpanExporter`
* [DemoServlet3InstrumentationModule](src/main/java/com/example/javaagent/instrumentation/DemoServlet3InstrumentationModule.java) - additional instrumentation

## Instrumentation customisation

There are several options to override or customise instrumentation provided by the upstream agent.
The following description follows one specific use-case:

> Instrumentation X from Otel distribution creates span that I don't like and I want to change it.

As an example, let us take some database client instrumentation that creates a span for database call
and extracts data from db connection to provide attributes for that span.

### I don't want this span at all
The easiest case. You can just pre-configure the agent in your extension and disable given instrumentation.

### I want to add/modify some attributes and their values does NOT depend on a specific db connection instance.
E.g. you want to add some data from call stack as span attribute. 
In this case just provide your custom `SpanProcessor`.
No need for touching instrumentation itself.

### I want to add/modify some attributes and their values depend on a specific db connection instance.
Write a _new_ instrumentation which injects its own advice into the same method as the original one.
Use `order` method to ensure it is run after the original instrumentation.
Now you can augment current span with new information.

See [DemoServlet3InstrumentationModule](instrumentation/servlet-3/src/main/java/com/example/javaagent/instrumentation/DemoServlet3InstrumentationModule.java).

### I want to remove some attributes
Write custom exporter or use attribute filtering functionality in Collector.

### I don't like Otel span at all. I want to significantly modify it and its lifecycle
Disable existing instrumentation.
Write a new one, which injects `Advice` into the same (or better) method as the original instrumentation.
Write your own `Advice` for this.
Use existing `Tracer` directly or extend it.
As you have your own `Advice`, you can control which `Tracer` you use.
