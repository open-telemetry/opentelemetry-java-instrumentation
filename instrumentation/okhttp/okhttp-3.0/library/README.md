# Library Instrumentation for OkHttp version 3.0 and higher

Provides OpenTelemetry instrumentation for [okhttp3](https://square.github.io/okhttp/).

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest
release](https://search.maven.org/search?q=g:io.opentelemetry.instrumentation%20AND%20a:opentelemetry-okhttp-3.0).

For Maven, add to your `pom.xml` dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-okhttp-3.0</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```groovy
implementation("io.opentelemetry.instrumentation:opentelemetry-okhttp-3.0:OPENTELEMETRY_VERSION")
```

### Usage

The instrumentation library provides an OkHttp `Call.Factory` implementation that wraps
an instance of the `OkHttpClient` to provide OpenTelemetry-based spans and context
propagation.

```java
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.okhttp.v3_0.OkHttpTelemetry;
import okhttp3.Call;
import okhttp3.OkHttpClient;

public class OkHttpConfiguration {

  //Use this Call.Factory implementation for making standard http client calls.
  public Call.Factory createTracedClient(OpenTelemetry openTelemetry) {
    return OkHttpTelemetry.builder(openTelemetry).build().newCallFactory(createClient());
  }

  //your configuration of the OkHttpClient goes here:
  private OkHttpClient createClient() {
    return new OkHttpClient.Builder().build();
  }
}
```
