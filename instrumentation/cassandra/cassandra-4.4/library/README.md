# Library Instrumentation for Cassandra version 4.4 and higher

Provides OpenTelemetry instrumentation for the [DataStax Java Driver for Apache Cassandra](https://docs.datastax.com/en/developer/java-driver/latest/),
enabling database client spans and metrics.

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest release](https://central.sonatype.com/artifact/io.opentelemetry.instrumentation/opentelemetry-cassandra-4.4).

For Maven, add to your `pom.xml` dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-cassandra-4.4</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```kotlin
implementation("io.opentelemetry.instrumentation:opentelemetry-cassandra-4.4:OPENTELEMETRY_VERSION")
```

### Usage

```java
import com.datastax.oss.driver.api.core.CqlSession;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.cassandra.v4_4.CassandraTelemetry;

// ...

// Get an OpenTelemetry instance
OpenTelemetry openTelemetry = ...;

// Create a CassandraTelemetry instance
CassandraTelemetry telemetry = CassandraTelemetry.create(openTelemetry);

// Create a CqlSession
CqlSession session = CqlSession.builder().build();

// Wrap the session
CqlSession tracedSession = telemetry.wrap(session);

// ... use the tracedSession to make requests
```

### Configured server target

To record `server.address` and `server.port` with stable database semantic conventions,
pass the complete original contact-point list when wrapping the session:

```java
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;

List<InetSocketAddress> contactPoints =
    Arrays.asList(
        new InetSocketAddress("cassandra1.example.com", 9042),
        new InetSocketAddress("cassandra2.example.com", 9042));
CqlSession session =
    CqlSession.builder()
        .addContactPoints(contactPoints)
        .withLocalDatacenter("datacenter1")
        .build();
CqlSession tracedSession = telemetry.wrap(session, contactPoints);
```

Include contact points from every source, including the session builder and driver configuration.
Do not use the current coordinator or discovered cluster nodes. The wrapper snapshots this list
without changing the session's connections.

`telemetry.wrap(session)` still traces operations but omits stable `server.address` and `server.port`.
Legacy database attributes are unchanged. The Java agent captures configured contact points
automatically and does not require this overload.
