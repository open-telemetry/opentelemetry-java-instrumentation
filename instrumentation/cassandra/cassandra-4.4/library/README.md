# Library Instrumentation for Cassandra version 4.4 and higher

Provides OpenTelemetry instrumentation for the [DataStax Java Driver for Apache Cassandra](https://docs.datastax.com/en/developer/java-driver/latest/).

This instrumentation generates CLIENT spans for each CQL query.

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest release](https://search.maven.org/search?q=g:io.opentelemetry.instrumentation%20AND%20a:opentelemetry-cassandra-4.4).

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
