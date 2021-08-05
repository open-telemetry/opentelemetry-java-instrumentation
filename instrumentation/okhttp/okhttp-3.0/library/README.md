# Manual Instrumentation for OkHttp3

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

The instrumentation library provides an OkHttp `Interceptor` implementation which instruments http
client calls, including context propagation on the outgoing request, and span attributes based on
the OpenTelemetry semantic conventions.

In addition, it is straightforward to add support for in-process context propagation, based on standard
OpenTelemetry APIs.

```java
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.okhttp.v3_0.OkHttpTracing;
import okhttp3.Dispatcher;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;

import java.util.concurrent.ExecutorService;

public class OkHttpConfiguration {

  public Interceptor createInterceptor(OpenTelemetry openTelemetry) {
    return OkHttpTracing.newBuilder(openTelemetry).build().newInterceptor();
  }

  public Dispatcher createDispatcher(ExecutorService executorService) {
    return new Dispatcher(Context.taskWrapping(executorService));
  }

  public OkHttpClient createClient(OpenTelemetry openTelemetry, ExecutorService executorService) {
    return new OkHttpClient.Builder()
        .dispatcher(createDispatcher(executorService))
        .addInterceptor(createInterceptor(openTelemetry))
        .build();
  }
}
```
