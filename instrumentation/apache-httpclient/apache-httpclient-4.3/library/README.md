# Library Instrumentation for Apache HttpClient version 4.3 and higher

Provides OpenTelemetry instrumentation for [Apache HttpClient](https://hc.apache.org/httpcomponents-client-ga/).

This instrumentation generates CLIENT spans for each HTTP request.

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest release](https://search.maven.org/search?q=g:io.opentelemetry.instrumentation%20AND%20a:opentelemetry-apache-httpclient-4.3).

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
CloseableHttpClient httpClient = telemetry.newHttpClient();

// ... use the httpClient to make requests
```
