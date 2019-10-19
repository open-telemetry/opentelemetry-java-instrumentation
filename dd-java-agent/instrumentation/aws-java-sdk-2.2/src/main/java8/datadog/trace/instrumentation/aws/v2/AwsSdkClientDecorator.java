package datadog.trace.instrumentation.aws.v2;

import datadog.trace.agent.decorator.HttpClientDecorator;
import datadog.trace.api.DDTags;
import datadog.trace.instrumentation.api.AgentSpan;
import java.net.URI;
import software.amazon.awssdk.awscore.AwsResponse;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.SdkResponse;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.SdkExecutionAttribute;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.SdkHttpResponse;

public class AwsSdkClientDecorator extends HttpClientDecorator<SdkHttpRequest, SdkHttpResponse> {
  public static final AwsSdkClientDecorator DECORATE = new AwsSdkClientDecorator();

  static final String COMPONENT_NAME = "java-aws-sdk";

  public AgentSpan onSdkRequest(final AgentSpan span, final SdkRequest request) {
    // S3
    request
        .getValueForField("Bucket", String.class)
        .ifPresent(name -> span.setTag("aws.bucket.name", name));
    // DynamoDB
    request
        .getValueForField("TableName", String.class)
        .ifPresent(name -> span.setTag("aws.table.name", name));
    // SQS
    request
        .getValueForField("QueueName", String.class)
        .ifPresent(name -> span.setTag("aws.queue.name", name));
    request
        .getValueForField("QueueUrl", String.class)
        .ifPresent(name -> span.setTag("aws.queue.url", name));
    // Kinesis
    request
        .getValueForField("StreamName", String.class)
        .ifPresent(name -> span.setTag("aws.stream.name", name));
    return span;
  }

  public AgentSpan onAttributes(final AgentSpan span, final ExecutionAttributes attributes) {

    final String awsServiceName = attributes.getAttribute(SdkExecutionAttribute.SERVICE_NAME);
    final String awsOperation = attributes.getAttribute(SdkExecutionAttribute.OPERATION_NAME);

    // Resource Name has to be set after the HTTP_URL because otherwise decorators overwrite it
    span.setTag(DDTags.RESOURCE_NAME, awsServiceName + "." + awsOperation);

    span.setTag("aws.agent", COMPONENT_NAME);
    span.setTag("aws.service", awsServiceName);
    span.setTag("aws.operation", awsOperation);

    return span;
  }

  // Not overriding the super.  Should call both with each type of response.
  public AgentSpan onResponse(final AgentSpan span, final SdkResponse response) {
    if (response instanceof AwsResponse) {
      span.setTag("aws.requestId", ((AwsResponse) response).responseMetadata().requestId());
    }
    return span;
  }

  @Override
  protected String service() {
    return COMPONENT_NAME;
  }

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"aws-sdk"};
  }

  @Override
  protected String component() {
    return COMPONENT_NAME;
  }

  @Override
  protected String method(final SdkHttpRequest request) {
    return request.method().name();
  }

  @Override
  protected URI url(final SdkHttpRequest request) {
    return request.getUri();
  }

  @Override
  protected String hostname(final SdkHttpRequest request) {
    return request.host();
  }

  @Override
  protected Integer port(final SdkHttpRequest request) {
    return request.port();
  }

  @Override
  protected Integer status(final SdkHttpResponse response) {
    return response.statusCode();
  }
}
