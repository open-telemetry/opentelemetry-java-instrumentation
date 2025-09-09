# Library Instrumentation for Elasticsearch REST Client version 7.0 and higher

Provides OpenTelemetry instrumentation for the [Elasticsearch REST Client](https://www.elastic.co/guide/en/elasticsearch/client/java-rest/current/java-rest-high.html), enabling database
client spans and metrics.

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest release](https://search.maven.org/search?q=g:io.opentelemetry.instrumentation%20AND%20a:opentelemetry-elasticsearch-rest-7.0).

For Maven, add to your `pom.xml` dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-elasticsearch-rest-7.0</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```kotlin
implementation("io.opentelemetry.instrumentation:opentelemetry-elasticsearch-rest-7.0:OPENTELEMETRY_VERSION")
```

### Usage

```java
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.elasticsearch.rest.v7_0.ElasticsearchRest7Telemetry;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;

// ...

// Get an OpenTelemetry instance
OpenTelemetry openTelemetry = ...;

// Create an ElasticsearchRest7Telemetry instance
ElasticsearchRest7Telemetry telemetry = ElasticsearchRest7Telemetry.create(openTelemetry);

// Create a RestClient
RestClient restClient = RestClient.builder(new HttpHost("localhost", 9200, "http")).build();

// Wrap the client
RestClient tracedClient = telemetry.wrap(restClient);

// ... use the tracedClient to make requests
```
