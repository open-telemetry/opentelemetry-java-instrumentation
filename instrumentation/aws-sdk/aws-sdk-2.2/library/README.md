# Library Instrumentation for AWS SDK for Java version 2.2 and higher

Provides OpenTelemetry instrumentation for the [AWS SDK for Java v2](https://github.com/aws/aws-sdk-java-v2).

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest release](https://central.sonatype.com/artifact/io.opentelemetry.instrumentation/opentelemetry-aws-sdk-2.2).

For Maven, add to your `pom.xml` dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-aws-sdk-2.2</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```kotlin
implementation("io.opentelemetry.instrumentation:opentelemetry-aws-sdk-2.2:OPENTELEMETRY_VERSION")
```

### Usage

For clients your application builds, register the interceptor on each client you want to instrument:

```java
AwsSdkTelemetry telemetry = AwsSdkTelemetry.create(openTelemetry);
DynamoDbClient client = DynamoDbClient.builder()
    .overrideConfiguration(ClientOverrideConfiguration.builder()
        .addExecutionInterceptor(telemetry.createExecutionInterceptor())
        .build())
    .build();
```

To register the interceptor automatically on all AWS SDK clients, use the
[autoconfigure module](../library-autoconfigure/README.md).

#### Wrap SQS and asynchronous Bedrock Runtime clients

These clients need both an interceptor and a wrapper. Register the interceptor on the builder as
shown above, then build and wrap the client using the same `AwsSdkTelemetry` instance.

Wrap synchronous and asynchronous SQS clients after building them:

```java
SqsClientBuilder sqsClientBuilder = SqsClient.builder();
...
SqsClient sqsClient = telemetry.instrument(sqsClientBuilder.build());
```

```java
SqsAsyncClientBuilder sqsAsyncClientBuilder = SqsAsyncClient.builder();
...
SqsAsyncClient sqsAsyncClient = telemetry.instrument(sqsAsyncClientBuilder.build());
```

Wrap the asynchronous Bedrock Runtime client after building it:

```java
BedrockRuntimeAsyncClientBuilder bedrockClientBuilder = BedrockRuntimeAsyncClient.builder();
...
BedrockRuntimeAsyncClient bedrockClient = telemetry.instrumentBedrockRuntimeClient(bedrockClientBuilder.build());
```

To capture Bedrock streaming response content, call `setGenaiCaptureMessageContent(true)` on the
`AwsSdkTelemetry` builder and use the resulting instance for both the interceptor and the wrapper.

## Trace propagation

The AWS SDK v2 instrumentation injects the current context into the outbound HTTP request's
`X-Amzn-Trace-Id` header using the
[AWS Trace Header](https://docs.aws.amazon.com/xray/latest/devguide/xray-concepts.html#xray-concepts-tracingheader)
format. This is the format recognized by AWS managed services.

For SQS `SendMessageBatch` operations, when X-Ray
propagation is enabled, the instrumentation also writes each message creation context to that
entry's `AWSTraceHeader` message system attribute. This per-message carrier is separate from the
shared HTTP request header and does not consume one of the ten user message attributes. The X-Ray
propagation setting controls both carriers. On SDK versions that do not support per-entry message
system attributes, the configured messaging propagator can still inject each creation context into
user message attributes when the experimental option is enabled and attribute capacity permits.

Additionally, you can enable an experimental option to use the configured propagator to inject into
message attributes (see [parent README](../../README.md)). This currently supports the following AWS APIs:

- SQS.SendMessage
- SQS.SendMessageBatch
- SNS.Publish
  (SNS.PublishBatch is not supported at the moment because it is not available in the minimum SDK
  version targeted by the instrumentation)

Note that injection will only happen if, after injection, a maximum of 10 attributes is used to not
run over API limitations set by AWS.

If this does not fulfill your use case, perhaps because you are
using the same SDK with a different non-AWS managed service, let us know so we can provide
configuration for this behavior.

## Development

### Testing

Some tests use recorded API responses to run through instrumentation. By default, recordings
are used, but if needing to add new tests/recordings or update existing ones, run the tests with
the `RECORD_WITH_REAL_API` environment variable set. AWS credentials will need to be correctly
configured to work.
