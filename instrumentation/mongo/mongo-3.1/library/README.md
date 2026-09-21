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
`MongoTelemetry` to the `MongoClientSettings` builder. Pass the configured seed
list to `createCommandListener`. Stable telemetry then reports those seeds as
the logical server target.

```java
OpenTelemetry openTelemetry = ...;

MongoTelemetry mongoTelemetry = MongoTelemetry.builder(openTelemetry).build();

List<ServerAddress> seeds = Collections.singletonList(
    new ServerAddress("localhost", 27017));
MongoClientSettings settings = MongoClientSettings.builder()
    .applyToClusterSettings(cluster -> cluster.hosts(seeds))
    .addCommandListener(mongoTelemetry.createCommandListener(seeds))
    .build();

// With Reactive Streams
MongoClient client = MongoClients.create(settings);
```
