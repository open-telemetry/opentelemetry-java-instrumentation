# AWS Java SDK v1 Instrumentation

Instrumentation for [AWS Java SDK v1](https://github.com/aws/aws-sdk-java).

## Trace propagation

The AWS SDK instrumentation injects the trace header using the
[AWS Trace Header](https://docs.aws.amazon.com/xray/latest/devguide/xray-concepts.html#xray-concepts-tracingheader)
format. This is the format recognized by AWS managed services.

For SQS batch sends under stable messaging semantic conventions, the instrumentation writes each
message creation context to that entry's `AWSTraceHeader` message system attribute. This uses the
AWS-defined per-message carrier and does not consume one of the ten user message attributes. The
instrumentation falls back to the shared request trace header when the SDK version does not support
per-entry message system attributes.
