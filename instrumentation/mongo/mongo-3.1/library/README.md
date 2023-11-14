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

The instrumentation is initialized by passing a `MongoTelemetry::newCommandListener()` to the `MongoClientSettings` builder. You must set the `OpenTelemetry` to use with the feature.

```java
OpenTelemetry openTelemetry = ...;

MongoTelemetry mongoTelemetry = MongoTelemetry.builder(openTelemetry).build();

MongoClientSettings settings = MongoClientSettings.builder()
    .applyConnectionString(ConnectionString("mongodb://localhost:27017"))
    .addCommandListener(mongoTelemetry.newCommandListener())
    .build();

// With Reactive Streams
MongoClient client = MongoClients.create(settings);
```
