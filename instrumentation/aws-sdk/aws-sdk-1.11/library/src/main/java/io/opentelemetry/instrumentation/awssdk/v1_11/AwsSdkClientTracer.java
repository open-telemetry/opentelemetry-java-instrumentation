/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import com.amazonaws.AmazonWebServiceResponse;
import com.amazonaws.Request;
import com.amazonaws.Response;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.extension.aws.AwsXrayPropagator;
import io.opentelemetry.instrumentation.api.tracer.HttpClientTracer;
import io.opentelemetry.instrumentation.api.tracer.net.NetPeerAttributes;
import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;

final class AwsSdkClientTracer extends HttpClientTracer<Request<?>, Request<?>, Response<?>> {

  private static final ClassValue<String> OPERATION_NAME =
      new ClassValue<String>() {
        @Override
        protected String computeValue(Class<?> type) {
          String ret = type.getSimpleName();
          ret = ret.substring(0, ret.length() - 7); // remove 'Request'
          return ret;
        }
      };

  static final String COMPONENT_NAME = "java-aws-sdk";

  private final NamesCache namesCache = new NamesCache();

  private final boolean captureExperimentalSpanAttributes;

  AwsSdkClientTracer(OpenTelemetry openTelemetry, boolean captureExperimentalSpanAttributes) {
    super(openTelemetry, new NetPeerAttributes());
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
  }

  @Override
  protected void inject(Context context, Request<?> request) {
    AwsXrayPropagator.getInstance().inject(context, request, AwsSdkInjectAdapter.INSTANCE);
  }

  @Override
  protected String spanNameForRequest(Request<?> request) {
    if (request == null) {
      return DEFAULT_SPAN_NAME;
    }
    String awsServiceName = request.getServiceName();
    Class<?> awsOperation = request.getOriginalRequest().getClass();
    return qualifiedOperation(awsServiceName, awsOperation);
  }

  public Context startSpan(SpanKind kind, Context parentContext, Request<?> request) {
    Context context = super.startSpan(kind, parentContext, request, request, -1);
    Span span = Span.fromContext(context);

    String awsServiceName = request.getServiceName();

    if (captureExperimentalSpanAttributes) {
      span.setAttribute("aws.agent", COMPONENT_NAME);
      span.setAttribute("aws.service", awsServiceName);
      span.setAttribute("aws.operation", extractOperationName(request));
      span.setAttribute("aws.endpoint", request.getEndpoint().toString());

      Object originalRequest = request.getOriginalRequest();
      String bucketName = RequestAccess.getBucketName(originalRequest);
      if (bucketName != null) {
        span.setAttribute("aws.bucket.name", bucketName);
      }
      String queueUrl = RequestAccess.getQueueUrl(originalRequest);
      if (queueUrl != null) {
        span.setAttribute("aws.queue.url", queueUrl);
      }
      String queueName = RequestAccess.getQueueName(originalRequest);
      if (queueName != null) {
        span.setAttribute("aws.queue.name", queueName);
      }
      String streamName = RequestAccess.getStreamName(originalRequest);
      if (streamName != null) {
        span.setAttribute("aws.stream.name", streamName);
      }
      String tableName = RequestAccess.getTableName(originalRequest);
      if (tableName != null) {
        span.setAttribute("aws.table.name", tableName);
      }
    }
    return context;
  }

  @Override
  public void onResponse(Span span, Response<?> response) {
    if (captureExperimentalSpanAttributes
        && response != null
        && response.getAwsResponse() instanceof AmazonWebServiceResponse) {
      AmazonWebServiceResponse<?> awsResp = (AmazonWebServiceResponse<?>) response.getAwsResponse();
      span.setAttribute("aws.requestId", awsResp.getRequestId());
    }
    super.onResponse(span, response);
  }

  private String qualifiedOperation(String service, Class<?> operation) {
    ConcurrentHashMap<String, String> cache = namesCache.get(operation);
    String qualified = cache.get(service);
    if (qualified == null) {
      qualified =
          service.replace("Amazon", "").trim()
              + '.'
              + operation.getSimpleName().replace("Request", "");
      cache.put(service, qualified);
    }
    return qualified;
  }

  @Override
  protected String method(Request<?> request) {
    return request.getHttpMethod().name();
  }

  @Override
  protected URI url(Request<?> request) {
    return request.getEndpoint();
  }

  @Override
  protected Integer status(Response<?> response) {
    return response.getHttpResponse().getStatusCode();
  }

  @Override
  protected String requestHeader(Request<?> request, String name) {
    return request.getHeaders().get(name);
  }

  @Override
  protected String responseHeader(Response<?> response, String name) {
    return response.getHttpResponse().getHeaders().get(name);
  }

  @Override
  protected TextMapSetter<Request<?>> getSetter() {
    // We override injection and don't want to have the base class do it accidentally.
    return null;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.aws-sdk-1.11";
  }

  static final class NamesCache extends ClassValue<ConcurrentHashMap<String, String>> {
    @Override
    protected ConcurrentHashMap<String, String> computeValue(Class<?> type) {
      return new ConcurrentHashMap<>();
    }
  }

  private static String extractOperationName(Request<?> request) {
    return OPERATION_NAME.get(request.getOriginalRequest().getClass());
  }
}
