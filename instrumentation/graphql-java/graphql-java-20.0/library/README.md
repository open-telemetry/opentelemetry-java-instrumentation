# Library Instrumentation for GraphQL Java version 20.0 and higher

Provides OpenTelemetry instrumentation for [GraphQL Java](https://www.graphql-java.com/).

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest
release](https://central.sonatype.com/artifact/io.opentelemetry.instrumentation/opentelemetry-graphql-java-12.0).

For Maven, add to your `pom.xml` dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-graphql-java-20.0</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```groovy
implementation("io.opentelemetry.instrumentation:opentelemetry-graphql-java-20.0:OPENTELEMETRY_VERSION")
```

### Usage

The instrumentation library provides a GraphQL Java `Instrumentation` implementation that can be
added to an instance of the `GraphQL` to provide OpenTelemetry-based spans.

```java
void configure(OpenTelemetry openTelemetry, GraphQL.Builder builder) {
  GraphQLTelemetry telemetry = GraphQLTelemetry.builder(openTelemetry).build();
  builder.instrumentation(telemetry.createInstrumentation());
}
```

### Options

`GraphQLTelemetryBuilder` exposes the following settings:

| Method                                                 | Default | Description                                                                                                                                                                                                                       |
| ------------------------------------------------------ | ------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `setCaptureQuery(boolean)`                             | `true`  | Whether to capture the query in the `graphql.document` span attribute.                                                                                                                                                            |
| `setQuerySanitizationEnabled(boolean)`                 | `true`  | Whether to remove sensitive information from the captured query.                                                                                                                                                                  |
| `setOperationNameInSpanNameEnabled(boolean)`           | `false` | Whether the GraphQL operation name is added to the span name. **WARNING**: operation name is client-provided and can have high cardinality.                                                                                       |
| `setDataFetcherInstrumentationEnabled(boolean)`        | `false` | Whether to create spans for data fetchers.                                                                                                                                                                                        |
| `setTrivialDataFetcherInstrumentationEnabled(boolean)` | `false` | Whether to create spans for trivial data fetchers (one that simply maps data from an object to a field).                                                                                                                          |
| `setOperationSpanEnabled(boolean)`                     | `true`  | Whether to create the GraphQL operation span. When disabled, no `GraphQL Operation` span is created; data fetcher spans, if enabled, are unaffected.                                                                              |
| `setAddAttributesToLocalRootSpan(boolean)`             | `false` | Whether to add GraphQL attributes and exception events to the local root span (typically the enclosing HTTP server span), in addition to or instead of the operation span.                                                        |
| `setPromoteErrorStatusToLocalRootSpan(boolean)`        | `false` | Whether to set the local root span status to `ERROR` when the result contains any errors. **WARNING**: marks the enclosing (e.g. server) span as errored for any GraphQL error, including partial errors on an HTTP 200 response. |
