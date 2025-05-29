# Library Instrumentation for GraphQL Java version 20.0 and higher

Provides OpenTelemetry instrumentation for [GraphQL Java](https://www.graphql-java.com/).

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest
release](https://search.maven.org/search?q=g:io.opentelemetry.instrumentation%20AND%20a:opentelemetry-graphql-java-12.0).

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
  builder.instrumentation(telemetry.newInstrumentation());
}
```
