# AWS Java SDK v2 Instrumentation

Instrumentation for [AWS Java SDK v2](https://github.com/aws/aws-sdk-java-v2).

## Usage

To instrument all AWS SDK clients include the `opentelemetry-aws-sdk-2.2-autoconfigure` submodule in your classpath.

To register instrumentation only on a specific SDK client, register the interceptor when creating it.

```java
// For tracing
AwsSdkTelemetry telemetry = AwsSdkTelemetry.create(openTelemetry);
DynamoDbClient client = DynamoDbClient.builder()
  .overrideConfiguration(ClientOverrideConfiguration.builder()
    .addExecutionInterceptor(telemetry.newExecutionInterceptor())
    .build())
  .build();

// For metrics (can be used independently of tracing)
MetricPublisher metricPublisher = new OpenTelemetryMetricPublisher(openTelemetry);

DynamoDbClient client = DynamoDbClient.builder()
  .overrideConfiguration(ClientOverrideConfiguration.builder()
    .addMetricPublisher(metricPublisher)
    .build())
  .build();
```

For SQS an additional step is needed
```java
SqsClientBuilder sqsClientBuilder = SqsClient.builder();
...
SqsClient sqsClient = telemetry.wrap(sqsClientBuilder.build());
```
```java
SqsAsyncClientBuilder sqsAsyncClientBuilder = SqsAsyncClient.builder();
...
SqsAsyncClient sqsAsyncClient = telemetry.wrap(sqsAsyncClientBuilder.build());
```

## Metrics

The AWS SDK instrumentation can publish AWS SDK metrics to OpenTelemetry. This includes metrics like:
- API call latencies
- Retry counts
- Throttling events
- And other AWS SDK metrics

To enable metrics, instantiate `OpenTelemetryMetricPublisher` (or your own decorator around it)
and register it with the client:

```java
// Minimal setup – uses the common ForkJoinPool and the default metric prefix "aws.sdk"
MetricPublisher metricPublisher = new OpenTelemetryMetricPublisher(openTelemetry);

// Optional customization
Executor executor = Executors.newFixedThreadPool(4);
String metricPrefix = "mycompany.aws.sdk"; // will be used as Meter instrumentation scope
Attributes staticAttributes = Attributes.builder().put("env", "prod").build();
MetricPublisher metricPublisher = new OpenTelemetryMetricPublisher(openTelemetry, metricPrefix, executor, staticAttributes);

DynamoDbClient client = DynamoDbClient.builder()
  .overrideConfiguration(ClientOverrideConfiguration.builder()
    .addMetricPublisher(metricPublisher)
    .build())
  .build();
```

The publisher emits the full hierarchy of AWS-SDK metrics (per-request, per-attempt, and HTTP)
to the OpenTelemetry SDK. Instrument names are prefixed with your `metricPrefix` (default
`aws.sdk.`) and attributes include service, operation name, retry count, success flag,
HTTP status code and error type when relevant. Attribute objects are cached internally
to minimize GC overhead.

## Trace propagation

The AWS SDK instrumentation always injects the trace header into the request
using the [AWS Trace Header](https://docs.aws.amazon.com/xray/latest/devguide/xray-concepts.html#xray-concepts-tracingheader) format.
This format is the only format recognized by AWS managed services, and populating will allow
propagating the trace through them.

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
