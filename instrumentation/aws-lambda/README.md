# AWS Lambda Instrumentation

We provide two packages for instrumenting AWS lambda functions.

- [aws-lambda-core-1.0](./aws-lambda-core-1.0/library) provides lightweight instrumentation of the Lambda core library, supporting
  all versions. Use this package if you only use `RequestStreamHandler` or know you don't use any event classes from
  `aws-lambda-java-events`. This also includes when you are using `aws-serverless-java-container` to run e.g., a
  Spring Boot application on Lambda.

- [aws-lambda-events-3.11](./aws-lambda-events-3.11/library) provides instrumentation of the Lambda library, including standard and custom event
  types, from `aws-lambda-java-events` 3.11+.

## SDK setup for wrappers

The wrappers use SDK autoconfiguration and export traces, metrics, and logs over OTLP by default.
Use the same `OPENTELEMETRY_VERSION` as your Lambda instrumentation dependency. The BOM also
manages that dependency's version.

For Maven, add to your `pom.xml`:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>io.opentelemetry.instrumentation</groupId>
      <artifactId>opentelemetry-instrumentation-bom-alpha</artifactId>
      <version>OPENTELEMETRY_VERSION</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>
  <dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-sdk-extension-autoconfigure</artifactId>
  </dependency>
  <dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```kotlin
implementation(platform("io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom-alpha:OPENTELEMETRY_VERSION"))
implementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")
implementation("io.opentelemetry:opentelemetry-exporter-otlp")
```

If you use OTLP, set `OTEL_EXPORTER_OTLP_ENDPOINT` to the URL of a collector or backend the function
can reach. The default, `localhost`, requires a collector in the Lambda execution environment.
If no signal uses OTLP, omit its exporter dependency and add the dependencies for the exporters
you configure.
