## Introduction

This repository serves as a collection of examples of extending functionality of OpenTelemetry Java instrumentation agent.
It demonstrates how to repackage the aforementioned agent adding custom functionality.
For every extension point provided by OpenTelemetry Java instrumentation, this repository contains an example of
its usage.

## General structure

This repository has three main submodules:

* `custom` contains all custom functionality, SPI and other extensions
* `agent` contains the main repackaging functionality and, optionally, an entry point to the agent, if one wishes to
customize that
* `smoke-tests` contains simple tests to verify that resulting agent builds and applies correctly

## Extensions examples

* [DemoIdGenerator](custom/src/main/java/io/opentelemetry/demo/DemoIdGenerator.java) - custom `IdGenerator`
* [DemoPropagator](custom/src/main/java/io/opentelemetry/demo/DemoPropagator.java) - custom `TextMapPropagator`
* [DemoPropertySource](custom/src/main/java/io/opentelemetry/demo/DemoPropertySource.java) - default configuration
* [DemoSampler](custom/src/main/java/io/opentelemetry/demo/DemoSampler.java) - custom `Sampler`
* [DemoSpanProcessor](custom/src/main/java/io/opentelemetry/demo/DemoSpanProcessor.java) - custom `SpanProcessor`
* [DemoSpanExporter](custom/src/main/java/io/opentelemetry/demo/DemoSpanExporter.java) - custom `SpanExporter`
* [DemoServlet3Instrumentation](custom/src/main/java/io/opentelemetry/demo/instrumentation/DemoServlet3Instrumentation.java) - additional instrumentation