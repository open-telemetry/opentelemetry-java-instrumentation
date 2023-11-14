# MongoDB library instrumentation

This package contains the library to help instrument MongoDB Client.

## Quickstart

### Dependencies

Replace OPENTELEMETRY_VERSION with the [latest release](https://central.sonatype.com/search?q=g:io.opentelemetry.instrumentation++a:opentelemetry-mongo-3.1&smo=true).

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
OpenTelemetry openTelemetry = initializeOpenTelemetryForMe();
MongoTelemetry mongoTelemetry = MongoTelemetry.builder(openTelemetry).build();

MongoClientSettings settings = MongoClientSettings.builder()
    .applyConnectionString(ConnectionString("mongodb://localhost:27017"))
    .addCommandListener(mongoTelemetry.newCommandListener())
    .build();

// With Reactive Streams
MongoClient client = MongoClients.create(settings);
```

A logging message example produced by `LoggingSpanExporter`:

```
INFO: 'insert test.MyCollection' : 4487bebe5fcfc91af2cd517685552a33 a56685e62bf8f87c CLIENT [tracer: io.opentelemetry.mongo-3.1:1.31.0-alpha] AttributesMap{data={db.operation=insert, db.mongodb.collection=MyCollection, net.peer.port=27017, db.name=test, db.connection_string=mongodb://localhost:27017, net.peer.name=localhost, db.statement={"insert": "MyCollection", "ordered": "?", "txnNumber": "?", "$db": "?", "$clusterTime": {"clusterTime": "?", "signature": {"hash": "?", "keyId": "?"}}, "lsid": {"id": "?"}, "documents": [{"_id": "?", "name": "?", "createdAt": "?", "updatedAt": "?"}]}, db.system=mongodb}, capacity=128, totalAddedValues=8}
```
