# Library Instrumentation for Apache Http client version 5.6

Provides OpenTelemetry instrumentation for [Apache Http Client 5.6](https://hc.apache.org/httpcomponents-client-5.6.x/).

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest
release]( https://central.sonatype.com/artifact/io.opentelemetry.instrumentation/opentelemetry-apache-httpclient-5.6).

For Maven, add to your `pom.xml` dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-apache-httpclient-5.6</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```groovy
implementation("io.opentelemetry.instrumentation:opentelemetry-apache-httpclient-5.6:OPENTELEMETRY_VERSION")
```

### Usage

The instrumentation library provides the class `ApacheHttpClientTelemetry` that has a builder
method and allows the creation of an instance of the `HttpClientBuilder` to provide
OpenTelemetry-based spans and context propagation:

```java
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.apachehttpclient.v5_6.ApacheHttpClientTelemetry;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;

public class ApacheHttpClientConfiguration {

  private OpenTelemetry openTelemetry;

  public ApacheHttpClientConfiguration(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  // creates a new http client builder for constructing http clients with opentelemetry instrumentation
  public HttpClientBuilder createBuilder() {
    return ApacheHttpClientTelemetry.builder(openTelemetry).build().createHttpClientBuilder();
  }

  // creates a new http client with opentelemetry instrumentation
  public HttpClient newHttpClient() {
    return ApacheHttpClientTelemetry.builder(openTelemetry).build().createHttpClient();
  }
}
```
