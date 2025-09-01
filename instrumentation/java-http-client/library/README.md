# Library Instrumentation for Java HTTP Client

Provides OpenTelemetry instrumentation for [Java HTTP Client](https://openjdk.org/groups/net/httpclient/intro.html).

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest
release](https://mvnrepository.com/artifact/io.opentelemetry.instrumentation/opentelemetry-java-http-client).

For Maven, add to your `pom.xml` dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-java-http-client</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```groovy
implementation("io.opentelemetry.instrumentation:opentelemetry-java-http-client:OPENTELEMETRY_VERSION")
```

### Usage

The instrumentation library contains an `HttpClient` wrapper that provides OpenTelemetry-based spans
and context propagation.

```java
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.httpclient.JavaHttpClientTelemetry;
import java.net.http.HttpClient;

import java.util.concurrent.ExecutorService;

public class JavaHttpClientConfiguration {

  //Use this HttpClient implementation for making standard http client calls.
  public HttpClient createTracedClient(OpenTelemetry openTelemetry) {
    return JavaHttpClientTelemetry.builder(openTelemetry).build().newHttpClient(createClient());
  }

  //your configuration of the Java HTTP Client goes here:
  private HttpClient createClient() {
    return HttpClient.newBuilder().build();
  }
}
```
