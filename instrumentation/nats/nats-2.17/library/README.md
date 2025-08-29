# Library Instrumentation for NATS version 2.17

Provides OpenTelemetry instrumentation for [NATS 2.17](https://github.com/nats-io/nats.java).

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest
release](https://search.maven.org/search?q=g:io.opentelemetry.instrumentation%20AND%20a:opentelemetry-nats-2.17).

For Maven, add to your `pom.xml` dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-nats-2.17</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```groovy
implementation("io.opentelemetry.instrumentation:opentelemetry-nats-2.17:OPENTELEMETRY_VERSION")
```

### Usage

The instrumentation library provides the class `NatsTelemetry` that has a builder
method and allows the creation of an instance of the `Connection` to provide
OpenTelemetry-based spans and context propagation:

```java
import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.nats.v2_17.NatsTelemetry;

public class OpenTelemetryNatsConnection {

  private OpenTelemetry openTelemetry;
  private NatsTelemetry telemetry;

  public OpenTelemetryNatsConnection(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
    this.telemetry = NatsTelemetry.create(openTelemetry);
  }

  // creates a new connection with opentelemetry instrumentation.
  // This will *not* instrument the connection's main inbox
  // if you're using the default NatsConnection implementation
  public Connection wrap(Connection connection) {
    return telemetry.wrap(connection);
  }

  // prefer wrapping the Options.Builder to get the full instrumentation
  // when using the default NatsConnection implementation
  public Connection create(Options.Builder builder) throws IOException, InterruptedException {
    Options options = telemetry.wrap(builder).build();
    Connection connection = Nats.connect(options);
    return wrap(connection);
  }
}
```

### Trace propagation

It's recommended to provide `Message` with a writable `Header` structure
to allow propagation between publishers and subscribers. Without headers,
the tracing context will not be propagated in the headers.

```java
import io.nats.client.impl.Headers;
import io.nats.client.impl.NatsMessage;

// don't
Message msg = NatsMessage.builder().subject("sub").build();

// do
Message msg = NatsMessage.builder().subject("sub").headers(new Headers()).build();
```
