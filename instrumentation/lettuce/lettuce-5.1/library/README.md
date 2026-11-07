# Library Instrumentation for Lettuce version 5.1 and higher

Provides OpenTelemetry instrumentation for [Lettuce](https://redis.github.io/lettuce/), enabling database client
spans and metrics.

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest release](https://central.sonatype.com/artifact/io.opentelemetry.instrumentation/opentelemetry-lettuce-5.1).

For Maven, add to your `pom.xml` dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-lettuce-5.1</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```kotlin
implementation("io.opentelemetry.instrumentation:opentelemetry-lettuce-5.1:OPENTELEMETRY_VERSION")
```

### Usage

```java
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.resource.ClientResources;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.lettuce.v5_1.LettuceTelemetry;

// Get an OpenTelemetry instance
OpenTelemetry openTelemetry = ...;

LettuceTelemetry lettuceTelemetry = LettuceTelemetry.create(openTelemetry);

ClientResources clientResources = ClientResources.builder()
    .tracing(lettuceTelemetry.createTracing())
    .build();

RedisClient redisClient = RedisClient.create(clientResources, "redis://localhost:6379");
StatefulRedisConnection<String, String> connection = redisClient.connect();
```

### Error classification

Lettuce 5.x tracing callbacks can report server replies and client failures as messages without
their exceptions. These failures use `error.type="_OTHER"` on spans and duration metrics because
the callback cannot distinguish their origin. When Lettuce supplies an exception, its class is
used for `error.type`.

Errors available only as `EXEC` result elements are not classified on the aggregate `EXEC` span
because Lettuce does not expose per-result failure provenance to its tracing callback.
On older versions, command spans may finish when a transaction member is acknowledged as `QUEUED`,
before a later member failure becomes available.

With Lettuce 6.0 and higher, command-aware callbacks distinguish server replies from client
failures. A dependable server reply uses its first nonempty ASCII-space-delimited token; failures
reported only as exceptions use the exception class. Successful operations omit `error.type`.
