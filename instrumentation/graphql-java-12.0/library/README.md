# Manual Instrumentation for GraphQL Java

Provides OpenTelemetry instrumentation for [GraphQL Java](https://www.graphql-java.com/).

## Quickstart

### Add these dependencies to your project:

Replace `OPENTELEMETRY_VERSION` with the latest stable
[release](https://mvnrepository.com/artifact/io.opentelemetry). `Minimum version: 1.13.0`

For Maven, add to your `pom.xml` dependencies:

```xml

<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-graphql-java-12.0</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```groovy
implementation("io.opentelemetry.instrumentation:opentelemetry-graphql-java-12.0:OPENTELEMETRY_VERSION")
```

### Usage

The instrumentation library provides a GraphQL Java `Instrumentation` implementation that can be
added to an instance of the `GraphQL` to provide OpenTelemetry-based spans.

```java
void configure(OpenTelemetry openTelemetry, GraphQL.Builder builder) {
  GraphQLTracing tracing = GraphQLTracing.builder(openTelemetry).build();
  builder.instrumentation(tracing.newInstrumentation());
}
```
