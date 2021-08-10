# Manual Instrumentation for OkHttp3 version 4.0.0+

Provides OpenTelemetry instrumentation for [okhttp3](https://square.github.io/okhttp/).

## Quickstart

### Add these dependencies to your project:

Replace `OPENTELEMETRY_VERSION` with the latest stable
[release](https://mvnrepository.com/artifact/io.opentelemetry). `Minimum version: 1.4.0`

For Maven, add to your `pom.xml` dependencies:

```xml

<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-okhttp-4.0</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```groovy
implementation("io.opentelemetry.instrumentation:opentelemetry-okhttp-4.0:OPENTELEMETRY_VERSION")
```

### Usage

The instrumentation library provides an OkHttp `Call.Factory` implementation which instruments http
client calls, including context propagation on the outgoing request, and span attributes based on
the OpenTelemetry semantic conventions.

```java
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.okhttp.v4_0.OkHttpTracing;
import okhttp3.OkHttpClient;
import okhttp3.Call;

import java.util.concurrent.ExecutorService;

public class OkHttpConfiguration {

  public Call.Factory createCallFactory(OkHttpClient okHttpClient) {
    return OkHttpTracing.newBuilder(openTelemetry)
        .build()
        .newCallFactory(okHttpClient);
  }

  public OkHttpClient createClient(OpenTelemetry openTelemetry, ExecutorService executorService) {
    //configure your OkHttpClient here
    return new OkHttpClient.Builder().build();
  }
}
```
