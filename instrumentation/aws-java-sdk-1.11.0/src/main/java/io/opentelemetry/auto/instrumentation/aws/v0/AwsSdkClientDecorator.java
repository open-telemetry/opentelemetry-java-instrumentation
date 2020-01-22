package io.opentelemetry.auto.instrumentation.aws.v0;

import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.AmazonWebServiceResponse;
import com.amazonaws.Request;
import com.amazonaws.Response;
import io.opentelemetry.auto.api.MoreTags;
import io.opentelemetry.auto.bootstrap.ContextStore;
import io.opentelemetry.auto.decorator.HttpClientDecorator;
import io.opentelemetry.trace.Span;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AwsSdkClientDecorator extends HttpClientDecorator<Request, Response> {

  static final String COMPONENT_NAME = "java-aws-sdk";

  private final Map<String, String> serviceNames = new ConcurrentHashMap<>();
  private final Map<Class, String> operationNames = new ConcurrentHashMap<>();
  private final ContextStore<AmazonWebServiceRequest, RequestMeta> contextStore;

  public AwsSdkClientDecorator(
      final ContextStore<AmazonWebServiceRequest, RequestMeta> contextStore) {
    this.contextStore = contextStore;
  }

  @Override
  public Span onRequest(final Span span, final Request request) {
    // Call super first because we override the resource name below.
    super.onRequest(span, request);

    final String awsServiceName = request.getServiceName();
    final AmazonWebServiceRequest originalRequest = request.getOriginalRequest();
    final Class<?> awsOperation = originalRequest.getClass();

    span.setAttribute("aws.agent", COMPONENT_NAME);
    span.setAttribute("aws.service", awsServiceName);
    span.setAttribute("aws.operation", awsOperation.getSimpleName());
    span.setAttribute("aws.endpoint", request.getEndpoint().toString());

    span.setAttribute(
        MoreTags.RESOURCE_NAME,
        remapServiceName(awsServiceName) + "." + remapOperationName(awsOperation));

    if (contextStore != null) {
      final RequestMeta requestMeta = contextStore.get(originalRequest);
      if (requestMeta != null) {
        final String bucketName = requestMeta.getBucketName();
        if (bucketName != null) {
          span.setAttribute("aws.bucket.name", bucketName);
        }
        final String queueUrl = requestMeta.getQueueUrl();
        if (queueUrl != null) {
          span.setAttribute("aws.queue.url", queueUrl);
        }
        final String queueName = requestMeta.getQueueName();
        if (queueName != null) {
          span.setAttribute("aws.queue.name", queueName);
        }
        final String streamName = requestMeta.getStreamName();
        if (streamName != null) {
          span.setAttribute("aws.stream.name", streamName);
        }
        final String tableName = requestMeta.getTableName();
        if (tableName != null) {
          span.setAttribute("aws.table.name", tableName);
        }
      }
    }

    return span;
  }

  @Override
  public Span onResponse(final Span span, final Response response) {
    if (response.getAwsResponse() instanceof AmazonWebServiceResponse) {
      final AmazonWebServiceResponse awsResp = (AmazonWebServiceResponse) response.getAwsResponse();
      span.setAttribute("aws.requestId", awsResp.getRequestId());
    }
    return super.onResponse(span, response);
  }

  private String remapServiceName(final String serviceName) {
    if (!serviceNames.containsKey(serviceName)) {
      serviceNames.put(serviceName, serviceName.replace("Amazon", "").trim());
    }
    return serviceNames.get(serviceName);
  }

  private String remapOperationName(final Class<?> awsOperation) {
    if (!operationNames.containsKey(awsOperation)) {
      operationNames.put(awsOperation, awsOperation.getSimpleName().replace("Request", ""));
    }
    return operationNames.get(awsOperation);
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
  protected String method(final Request request) {
    return request.getHttpMethod().name();
  }

  @Override
  protected URI url(final Request request) throws URISyntaxException {
    return new URI(request.getEndpoint().toString());
  }

  @Override
  protected String hostname(final Request request) {
    return null;
  }

  @Override
  protected Integer port(final Request request) {
    return null;
  }

  @Override
  protected Integer status(final Response response) {
    return response.getHttpResponse().getStatusCode();
  }
}
