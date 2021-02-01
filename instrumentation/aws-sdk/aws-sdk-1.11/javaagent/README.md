# AWS Java SDK v1 Instrumentation

Instrumentation for [AWS Java SDK v1](https://github.com/aws/aws-sdk-java).

## Trace propagation

The AWS SDK instrumentation currently only supports injecting the trace header into the request
using the [AWS Trace Header](https://docs.aws.amazon.com/xray/latest/devguide/xray-concepts.html#xray-concepts-tracingheader) format.
This format is the only format recognized by AWS managed services, and populating will allow
propagating the trace through them. If this does not fulfill your use case, perhaps because you are
using the same SDK with a different non-AWS managed service, let us know so we can provide
configuration for this behavior.
