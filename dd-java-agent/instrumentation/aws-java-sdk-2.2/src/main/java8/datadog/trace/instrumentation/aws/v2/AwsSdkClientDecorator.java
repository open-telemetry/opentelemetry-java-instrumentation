package datadog.trace.instrumentation.aws.v2;

import datadog.trace.agent.decorator.HttpClientDecorator;
import datadog.trace.api.DDTags;
import io.opentracing.Span;
import java.net.URI;
import software.amazon.awssdk.awscore.AwsResponse;
import software.amazon.awssdk.core.SdkResponse;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.SdkExecutionAttribute;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.SdkHttpResponse;

public class AwsSdkClientDecorator extends HttpClientDecorator<SdkHttpRequest, SdkHttpResponse> {
  public static final AwsSdkClientDecorator DECORATE = new AwsSdkClientDecorator();

  static final String COMPONENT_NAME = "java-aws-sdk";

  public Span onAttributes(final Span span, final ExecutionAttributes attributes) {

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
  public Span onResponse(final Span span, final SdkResponse response) {
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
