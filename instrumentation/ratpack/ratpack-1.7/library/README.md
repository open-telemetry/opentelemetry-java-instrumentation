# Library Instrumentation for Ratpack version 1.7 and higher

Provides OpenTelemetry instrumentation for [Ratpack](https://ratpack.io/), enabling HTTP client and
server spans and metrics.

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest release](https://central.sonatype.com/artifact/io.opentelemetry.instrumentation/opentelemetry-ratpack-1.7).

For Maven, add to your `pom.xml` dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-ratpack-1.7</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```kotlin
implementation("io.opentelemetry.instrumentation:opentelemetry-ratpack-1.7:OPENTELEMETRY_VERSION")
```

### Usage

The instrumentation library provides implementations for both server and client instrumentation
that wrap Ratpack components.

```java
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.ratpack.v1_7.RatpackServerTelemetry;
import io.opentelemetry.instrumentation.ratpack.v1_7.RatpackClientTelemetry;
import ratpack.server.RatpackServer;
import ratpack.http.client.HttpClient;

public class RatpackConfiguration {

  // Create a server with OpenTelemetry instrumentation
  public RatpackServer createTracedServer(OpenTelemetry openTelemetry) throws Exception {
    RatpackServerTelemetry serverTelemetry = RatpackServerTelemetry.create(openTelemetry);
    return RatpackServer.start(server -> {
      server.registryOf(serverTelemetry::configureRegistry);
      server.handlers(chain ->
        chain.get(ctx -> ctx.render("Hello, World!"))
      );
    });
  }

  // Create an instrumented HttpClient
  public HttpClient createTracedClient(OpenTelemetry openTelemetry) {
    RatpackClientTelemetry clientTelemetry = RatpackClientTelemetry.create(openTelemetry);
    return clientTelemetry.instrument(createClient());
  }

  // Configuration of the HttpClient goes here
  private HttpClient createClient() {
    return HttpClient.of(spec -> {});
  }
}
```
