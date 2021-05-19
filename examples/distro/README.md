## Introduction

This repository serves as a collection of examples of extending functionality of OpenTelemetry Java instrumentation agent.
It demonstrates how to repackage the aforementioned agent adding custom functionality.
For every extension point provided by OpenTelemetry Java instrumentation, this repository contains an example of
its usage.

## General structure

This repository has four main submodules:

* `custom` contains all custom functionality, SPI and other extensions
* `agent` contains the main repackaging functionality and, optionally, an entry point to the agent, if one wishes to
customize that
* `instrumentation` contains custom instrumentations added by vendor
* `smoke-tests` contains simple tests to verify that resulting agent builds and applies correctly

## Extensions examples

* [DemoIdGenerator](custom/src/main/java/com/example/javaagent/DemoIdGenerator.java) - custom `IdGenerator`
* [DemoPropagator](custom/src/main/java/com/example/javaagent/DemoPropagator.java) - custom `TextMapPropagator`
* [DemoPropertySource](custom/src/main/java/com/example/javaagent/DemoPropertySource.java) - default configuration
* [DemoSampler](custom/src/main/java/com/example/javaagent/DemoSampler.java) - custom `Sampler`
* [DemoSpanProcessor](custom/src/main/java/com/example/javaagent/DemoSpanProcessor.java) - custom `SpanProcessor`
* [DemoSpanExporter](custom/src/main/java/com/example/javaagent/DemoSpanExporter.java) - custom `SpanExporter`
* [DemoServlet3InstrumentationModule](instrumentation/servlet-3/src/main/java/com/example/javaagent/instrumentation/DemoServlet3InstrumentationModule.java) - additional instrumentation

## Instrumentation customisation

There are several options to override or customise instrumentation provided by the upstream agent.
The following description follows one specific use-case:

> Instrumentation X from Otel distribution creates span that I don't like and I want to change it in my vendor distro.

As an example, let us take some database client instrumentation that creates a span for database call
and extracts data from db connection to provide attributes for that span.

### I don't want this span at all
The easiest case. You can just pre-configure your distribution and disable given instrumentation.

### I want to add/modify some attributes and their values does NOT depend on a specific db connection instance.
E.g. you want to add some data from call stack as span attribute. 
In this case just provide your custom `SpanProcessor`.
No need for touching instrumentation itself.

### I want to add/modify some attributes and their values depend on a specific db connection instance.
Write a _new_ instrumentation which injects its own advice into the same method as the original one.
Use `getOrder` method to ensure it is run after the original instrumentation.
Now you can augment current span with new information.

See [DemoServlet3Instrumentation](instrumentation/servlet-3/src/main/java/com/example/javaagent/instrumentation/DemoServlet3Instrumentation.java).

### I want to remove some attributes
Write custom exporter or use attribute filtering functionality in Collector.

### I don't like Otel span at all. I want to significantly modify it and its lifecycle
Disable existing instrumentation.
Write a new one, which injects `Advice` into the same (or better) method as the original instrumentation.
Write your own `Advice` for this.
Use existing `Tracer` directly or extend it.
As you have your own `Advice`, you can control which `Tracer` you use.
