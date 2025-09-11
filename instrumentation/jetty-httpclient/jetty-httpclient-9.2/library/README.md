# Library Instrumentation for Jetty HttpClient version 9.2 and higher

Provides OpenTelemetry instrumentation for the [Jetty HttpClient](https://github.com/jetty/jetty.project/tree/jetty-9.4.x), enabling http client spans
and metrics.

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest release](https://search.maven.org/search?q=g:io.opentelemetry.instrumentation%20AND%20a:opentelemetry-jetty-httpclient-9.2).

For Maven, add to your `pom.xml` dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-jetty-httpclient-9.2</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```kotlin
implementation("io.opentelemetry.instrumentation:opentelemetry-jetty-httpclient-9.2:OPENTELEMETRY_VERSION")
```

### Usage

```java
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.jetty.httpclient.v9_2.JettyClientTelemetry;
import org.eclipse.jetty.client.HttpClient;

// ...

// Get an OpenTelemetry instance
OpenTelemetry openTelemetry = ...;

// Create a JettyClientTelemetry instance
JettyClientTelemetry telemetry = JettyClientTelemetry.create(openTelemetry);

// Get a traced HttpClient
HttpClient httpClient = telemetry.getHttpClient();

// ... use the httpClient to make requests
```
