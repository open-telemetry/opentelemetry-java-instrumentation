# Message Handler

This package contains instrumentation for message systems.

The instrumentation will process messages and wrap the calls in a span with appropriate span attributes and span links.

## Available Message Handlers
- `io.opentelemetry.instrumentation.awssdk.v2_2.SqsMessageHandler` - Process SQS messages for the AWS SDK library. This is found in io.opentelemetry.aws-sdk-2.2.
- `io.opentelemetry.instrumentation.awslambdaevents.v2_2.SqsMessageHandler` - Process SQS messages for the Lambda instrumentation. This is found in io.opentelemetry.aws-lambda-events-2.2.
