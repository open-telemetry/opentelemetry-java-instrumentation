# Library Instrumentation for AWS SDK for Java version 1.11 and higher

Provides OpenTelemetry instrumentation for the [AWS SDK for Java](https://aws.amazon.com/sdk-for-java/).

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest release](https://central.sonatype.com/artifact/io.opentelemetry.instrumentation/opentelemetry-aws-sdk-1.11).

For Maven, add to your `pom.xml` dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-aws-sdk-1.11</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```kotlin
implementation("io.opentelemetry.instrumentation:opentelemetry-aws-sdk-1.11:OPENTELEMETRY_VERSION")
```

### Usage

The instrumentation library provides a `RequestHandler2` that can be added to any AWS SDK v1
client builder to provide spans and context propagation.

```java
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.awssdk.v1_11.AwsSdkTelemetry;

public class AwsSdkConfiguration {

  // Use this to create instrumented AWS SDK clients.
  public AmazonS3 createS3Client(OpenTelemetry openTelemetry) {
    return AmazonS3ClientBuilder.standard()
        .withRequestHandlers(
            AwsSdkTelemetry.builder(openTelemetry)
                .build()
                .newRequestHandler())
        .build();
  }

  // This pattern works for all AWS SDK v1 client builders (S3, SQS, DynamoDB, etc.).
}
```
