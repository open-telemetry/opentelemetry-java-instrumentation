# Library Instrumentation for Java HTTP Server

Provides OpenTelemetry instrumentation for [Java HTTP Server](https://docs.oracle.com/en/java/javase/21/docs/api/jdk.httpserver/module-summary.html).

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest
release](https://search.maven.org/search?q=g:io.opentelemetry.instrumentation%20AND%20a:opentelemetry-java-http-server).

For Maven, add to your `pom.xml` dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-java-http-server</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```groovy
implementation("io.opentelemetry.instrumentation:opentelemetry-java-http-server:OPENTELEMETRY_VERSION")
```

### Usage

The instrumentation library contains a `Filter` wrapper that provides OpenTelemetry-based spans
and context propagation.

```java

import java.io.IOException;
import java.net.InetSocketAddress;

import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpServer;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;

public class Application {

  static void main(String args) throws IOException {

    final HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
    final HttpContext context =
        server.createContext(
            "/",
            ctx -> {
              // http logic
            });

    OpenTelemetry openTelemetry = //...

    JavaHttpServerTelemetry.create(openTelemetry).configure(context);
  }
}
```
