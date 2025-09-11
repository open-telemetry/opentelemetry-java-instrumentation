# Library Instrumentation for Armeria version 1.3 and higher

Provides OpenTelemetry instrumentation for [Armeria](https://armeria.dev/), enabling HTTP client spans and metrics,
and HTTP server spans and metrics.

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest release](https://central.sonatype.com/artifact/io.opentelemetry.instrumentation/opentelemetry-armeria-1.3).

For Maven, add to your `pom.xml` dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-armeria-1.3</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```kotlin
implementation("io.opentelemetry.instrumentation:opentelemetry-armeria-1.3:OPENTELEMETRY_VERSION")
```

### Usage

#### Server

```java
import com.linecorp.armeria.server.Server;
import com.linecorp.armeria.server.ServerBuilder;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.armeria.v1_3.ArmeriaServerTelemetry;

// ...

// Get an OpenTelemetry instance
OpenTelemetry openTelemetry = ...;

// Create an ArmeriaServerTelemetry instance
ArmeriaServerTelemetry telemetry = ArmeriaServerTelemetry.create(openTelemetry);

// Add the decorator to your server builder
Server server = Server.builder()
    .decorator(telemetry.newDecorator())
    // ... other server configuration
    .build();
```

#### Client

```java
import com.linecorp.armeria.client.ClientBuilder;
import com.linecorp.armeria.client.WebClient;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.armeria.v1_3.ArmeriaClientTelemetry;

// ...

// Get an OpenTelemetry instance
OpenTelemetry openTelemetry = ...;

// Create an ArmeriaClientTelemetry instance
ArmeriaClientTelemetry telemetry = ArmeriaClientTelemetry.create(openTelemetry);

// Add the decorator to your client builder
WebClient client = new ClientBuilder("http://my-service.com")
    .decorator(telemetry.newDecorator())
    // ... other client configuration
    .build(WebClient.class);
```
