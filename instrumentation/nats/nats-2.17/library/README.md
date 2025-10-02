# Library Instrumentation for NATS version 2.17

Provides OpenTelemetry instrumentation for [NATS Client](https://github.com/nats-io/nats.java).

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest
release](https://central.sonatype.com/artifact/io.opentelemetry.instrumentation/opentelemetry-nats-2.17).

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
OpenTelemetry-based instrumentation:

```java
import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.nats.v2_17.NatsTelemetry;
import java.io.IOException;

public class OpenTelemetryNatsConnection {

  private OpenTelemetry openTelemetry;
  private NatsTelemetry telemetry;

  public OpenTelemetryNatsConnection(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
    this.telemetry = NatsTelemetry.create(openTelemetry);
  }

  public Connection newConnection() throws IOException, InterruptedException {
    return newConnection(Options.builder());
  }

  public Connection newConnection(Options.Builder options) throws IOException, InterruptedException {
    return telemetry.newConnection(options.build(), Nats::connect);
  }

}
```
