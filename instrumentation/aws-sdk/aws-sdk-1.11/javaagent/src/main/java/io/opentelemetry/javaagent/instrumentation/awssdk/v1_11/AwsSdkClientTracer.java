/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v1_11;

import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.AmazonWebServiceResponse;
import com.amazonaws.Request;
import com.amazonaws.Response;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator.Setter;
import io.opentelemetry.instrumentation.api.tracer.HttpClientOperation;
import io.opentelemetry.instrumentation.api.tracer.HttpClientTracer;
import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;

public class AwsSdkClientTracer extends HttpClientTracer<Request<?>, Request<?>, Response<?>> {

  static final String COMPONENT_NAME = "java-aws-sdk";

  private static final AwsSdkClientTracer TRACER = new AwsSdkClientTracer();

  public static AwsSdkClientTracer tracer() {
    return TRACER;
  }

  private final NamesCache namesCache = new NamesCache();

  public AwsSdkClientTracer() {}

  public HttpClientOperation<Response<?>> startOperation(
      Request<?> request, RequestMeta requestMeta) {

    Context parentContext = Context.current();
    if (inClientSpan(parentContext)) {
      return HttpClientOperation.noop();
    }
    SpanBuilder spanBuilder = spanBuilder(parentContext, request);

    String awsServiceName = request.getServiceName();
    AmazonWebServiceRequest originalRequest = request.getOriginalRequest();
    Class<?> awsOperation = originalRequest.getClass();

    spanBuilder.setAttribute("aws.agent", COMPONENT_NAME);
    spanBuilder.setAttribute("aws.service", awsServiceName);
    spanBuilder.setAttribute("aws.operation", awsOperation.getSimpleName());
    spanBuilder.setAttribute("aws.endpoint", request.getEndpoint().toString());

    if (requestMeta != null) {
      spanBuilder.setAttribute("aws.bucket.name", requestMeta.getBucketName());
      spanBuilder.setAttribute("aws.queue.url", requestMeta.getQueueUrl());
      spanBuilder.setAttribute("aws.queue.name", requestMeta.getQueueName());
      spanBuilder.setAttribute("aws.stream.name", requestMeta.getStreamName());
      spanBuilder.setAttribute("aws.table.name", requestMeta.getTableName());
    }
    Span span = spanBuilder.startSpan();
    Context context = withClientSpan(parentContext, span);
    inject(request, context);
    return newOperation(context, parentContext);
  }

  @Override
  public void onResponse(Span span, Response<?> response) {
    if (response != null && response.getAwsResponse() instanceof AmazonWebServiceResponse) {
      AmazonWebServiceResponse awsResp = (AmazonWebServiceResponse) response.getAwsResponse();
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
  protected Setter<Request<?>> getSetter() {
    return AwsSdkInjectAdapter.INSTANCE;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.aws-sdk";
  }

  static final class NamesCache extends ClassValue<ConcurrentHashMap<String, String>> {
    @Override
    protected ConcurrentHashMap<String, String> computeValue(Class<?> type) {
      return new ConcurrentHashMap<>();
    }
  }
}
