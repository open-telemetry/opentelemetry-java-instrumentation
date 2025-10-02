# Library Instrumentation for Helidon

Provides OpenTelemetry instrumentation for [Helidon](https://helidon.io/).

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest
release](https://search.maven.org/search?q=g:io.opentelemetry.instrumentation%20AND%20a:opentelemetry-helidon-4.3).

For Maven, add to your `pom.xml` dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-helidon-4.3</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```groovy
implementation("io.opentelemetry.instrumentation:opentelemetry-helidon-4.3:OPENTELEMETRY_VERSION")
```

### Usage

The instrumentation library contains an `HttpFeature` that provides OpenTelemetry-based spans
and context propagation.

```java
import java.io.IOException;

import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import io.opentelemetry.api.OpenTelemetry;

public class Application {

  static void main(String args) throws IOException {

    OpenTelemetry openTelemetry = // ...
    WebServer.builder()
        .addRouting(
            HttpRouting.builder()
                .addFeature(HelidonTelemetry.create(openTelemetry))
                .get("/greet", (req, res) -> res.send("Hello World!")))
        .build();
  }
}
```
