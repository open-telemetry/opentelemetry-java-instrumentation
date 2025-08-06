# Library Instrumentation for OpenAI Java SDK version 1.1.0 and higher

Provides OpenTelemetry instrumentation for [openai-java](https://github.com/openai/openai-java/).

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest
release](https://search.maven.org/search?q=g:io.opentelemetry.instrumentation%20AND%20a:opentelemetry-openai-java-1.1).

For Maven, add to your `pom.xml` dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-openai-java-1.1</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```groovy
implementation("io.opentelemetry.instrumentation:opentelemetry-openai-java-1.1:OPENTELEMETRY_VERSION")
```

### Usage

The instrumentation library provides a wrapper for `OpenAIClient` that provide OpenTelemetry-based
spans, metrics and logs.

```java
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.openai.v1_1.OpenAITelemetry;
import com.openai.client.OpenAIClient;

public class OpenAIClientConfiguration {

  //Use this client to capture telemetry.
  public OpenAIClient createOtelClient(OpenTelemetry openTelemetry) {
    return OpenAITelemetry.builder(openTelemetry).build().wrap(createClient());
  }

  //your configuration of the OpenAIClient goes here:
  private OpenAIClient createClient() {
    return ...;
  }
}
```
