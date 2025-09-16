# Library Instrumentation for Ratpack version 1.7 and higher

Provides OpenTelemetry instrumentation for [Ratpack](https://ratpack.io/), enabling HTTP client and server spans and
metrics.

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

```java
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.ratpack.v1_7.RatpackServerTelemetry;
import io.opentelemetry.instrumentation.ratpack.v1_7.RatpackClientTelemetry;
import ratpack.server.RatpackServer;
import ratpack.http.client.HttpClient;

public class RatpackExample {
  public static void main(String[] args) throws Exception {
    // Get an OpenTelemetry instance
    OpenTelemetry openTelemetry = ...;

    // Server instrumentation
    RatpackServerTelemetry serverTelemetry = RatpackServerTelemetry.create(openTelemetry);
    RatpackServer.start(server -> {
      server.registryOf(serverTelemetry::configureRegistry);
      server.handlers(chain ->
        chain.get(ctx -> ctx.render("Hello, World!"))
      );
    });

    // Client instrumentation
    RatpackClientTelemetry clientTelemetry = RatpackClientTelemetry.create(openTelemetry);
    HttpClient instrumentedHttpClient = clientTelemetry.instrument(HttpClient.of(spec -> {}));
  }
}
```
