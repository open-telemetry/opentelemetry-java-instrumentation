# AWS Java SDK v2 Instrumentation

Instrumentation for [AWS Java SDK v2](https://github.com/aws/aws-sdk-java-v2).

## Usage

To instrument all AWS SDK clients include the `opentelemetry-aws-sdk-2.2-autoconfigure` submodule in your classpath.

To register instrumentation only on a specific SDK client, register the interceptor when creating it.

```java
DynamoDbClient client = DynamoDbClient.builder()
  .overrideConfiguration(ClientOverrideConfiguration.builder()
    .addExecutionInterceptor(AwsSdk.newInterceptor()))
    .build())
  .build();
```

## Trace propagation

The AWS SDK instrumentation currently only supports injecting the trace header into the request
using the [AWS Trace Header](https://docs.aws.amazon.com/xray/latest/devguide/xray-concepts.html#xray-concepts-tracingheader) format.
This format is the only format recognized by AWS managed services, and populating will allow
propagating the trace through them. If this does not fulfill your use case, perhaps because you are
using the same SDK with a different non-AWS managed service, let us know so we can provide
configuration for this behavior.

## Using SqsMessageHandler
This instrumentation takes a collection of SQS messages.
A span wraps the function call doHandle with appropriate span attributes and span links.
Span links comes from each of the messages as if this were a batch of messages.

1. Setup SqsMessageHandler with your business logic. Pass in your OpenTelemetry, the name of the destination, and the span kind.
2. Call the "handle" method on SqsMessageHandler and pass in your collection of messages.
3. Under the hood it will call the "doHandle" method.

```java
OpenTelemetry openTelemetry;
Collection<Message> messages;

SqsMessageHandler messageHandler =
  new SqsMessageHandler(openTelemetry "destination", SpanKindExtractor.alwaysServer()) {
    @Override
    protected Void doHandle(Collection<Message> request) {
        // My business logic
        return null;
    }
};

messageHandler.handle(messages);
```
