# Library Instrumentation for Apache HttpClient version 4.3 and higher

Provides OpenTelemetry instrumentation for the [Apache HttpClient](https://hc.apache.org/httpcomponents-client-ga/), enabling HTTP client spans and metrics.

This instrumentation does not automatically capture `network.peer.address` or `network.peer.port`. The route target does not reliably identify the connected socket peer, particularly when an HTTP proxy is used.

`server.address` and `server.port` identify the logical destination. They can be used for destination-based grouping, but are not substitutes for actual socket peer information.

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest release](https://central.sonatype.com/artifact/io.opentelemetry.instrumentation/opentelemetry-apache-httpclient-4.3).

For Maven, add to your `pom.xml` dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-apache-httpclient-4.3</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```kotlin
implementation("io.opentelemetry.instrumentation:opentelemetry-apache-httpclient-4.3:OPENTELEMETRY_VERSION")
```

### Usage

```java
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.apachehttpclient.v4_3.ApacheHttpClientTelemetry;
import org.apache.http.impl.client.CloseableHttpClient;

// ...

// Get an OpenTelemetry instance
OpenTelemetry openTelemetry = ...;

// Create an ApacheHttpClientTelemetry instance
ApacheHttpClientTelemetry telemetry = ApacheHttpClientTelemetry.create(openTelemetry);

// Get a traced HttpClient
CloseableHttpClient httpClient = telemetry.createHttpClient();

// ... use the httpClient to make requests
```
