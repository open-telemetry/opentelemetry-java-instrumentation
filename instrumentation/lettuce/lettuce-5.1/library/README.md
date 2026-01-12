# Library Instrumentation for Lettuce version 5.1 and higher

Provides OpenTelemetry instrumentation for [Lettuce](https://lettuce.io/), enabling database client
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
    .tracing(lettuceTelemetry.newTracing())
    .build();

RedisClient redisClient = RedisClient.create(clientResources, "redis://localhost:6379");
StatefulRedisConnection<String, String> connection = redisClient.connect();
```
