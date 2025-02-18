# OpenTelemetry Java Agent for ActiveJ Framework

This repository provides an OpenTelemetry Java agent specifically designed to instrument applications built on the [ActiveJ framework](https://activej.io/). The agent enables distributed tracing, context propagation, and telemetry data collection for ActiveJ-based HTTP servers, making it easier to monitor and debug applications in distributed systems.

## Table of Contents

1. [Overview](#overview)
2. [Features](#features)
3. [Prerequisites](#prerequisites)
4. [Installation & Usage](#installation)

---

## Overview

The OpenTelemetry Java agent for ActiveJ integrates with the OpenTelemetry API to provide automatic instrumentation for ActiveJ HTTP servers. It captures trace context from incoming HTTP requests, propagates it through responses, and enriches telemetry data with HTTP-specific attributes such as request methods, headers, status codes, and URL components.

This agent is particularly useful for applications that rely on ActiveJ's high-performance, event-driven architecture and need observability in distributed systems.

---

## Features

- **Distributed Tracing**: Automatically propagates trace context across service boundaries using the `traceparent` header.
- **HTTP Attribute Extraction**: Captures detailed HTTP attributes (e.g., method, path, query, headers) for enriched telemetry data.
- **Error Handling**: Handles exceptions and maps them to appropriate HTTP status codes for better error visibility.
- **Compatibility**: Works seamlessly with OpenTelemetry's Java instrumentation framework and exporters (e.g., Jaeger, Zipkin, HyperDX).

---

## Prerequisites

Before using this agent, ensure you have the following:

- Java 8 or higher
- ActiveJ framework
- An OpenTelemetry collector or backend (e.g., Jaeger, Zipkin, HyperDX) for visualizing traces

---

## Installation

### Using the Java Agent JAR

1. Download the latest release of the OpenTelemetry Java agent JAR file.
2. Add the agent to your application's JVM arguments:

   ```bash
   java -javaagent:/path/to/opentelemetry-java-agent.jar -jar your-application.jar
   ```
