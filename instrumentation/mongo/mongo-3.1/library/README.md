# MongoDB library instrumentation

This package contains the library to help instrument MongoDB Client.

## Quickstart

### Dependencies

Replace OPENTELEMETRY_VERSION with the [latest release](https://central.sonatype.com/search?q=g%3Aio.opentelemetry.instrumentation+a%3Aopentelemetry-mongo-3.1).

For Maven, add to your `pom.xml` dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-mongo-3.1</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```gradle
implementation("io.opentelemetry.instrumentation:instrumentation:opentelemetry-mongo-3.1:OPENTELEMETRY_VERSION")
```

## Usage

The instrumentation is initialized by passing a command listener from
`MongoTelemetry` to the `MongoClientSettings` builder. For a client configured
with exactly one server, pass the same `ServerAddress` to
`createCommandListener`. Stable telemetry then reports that configured server
as its logical target.

```java
OpenTelemetry openTelemetry = ...;

MongoTelemetry mongoTelemetry = MongoTelemetry.builder(openTelemetry).build();

ServerAddress serverAddress = new ServerAddress("localhost", 27017);
MongoClientSettings settings = MongoClientSettings.builder()
    .applyToClusterSettings(
        cluster -> cluster.hosts(Collections.singletonList(serverAddress)))
    .addCommandListener(mongoTelemetry.createCommandListener(serverAddress))
    .build();

// With Reactive Streams
MongoClient client = MongoClients.create(settings);
```
