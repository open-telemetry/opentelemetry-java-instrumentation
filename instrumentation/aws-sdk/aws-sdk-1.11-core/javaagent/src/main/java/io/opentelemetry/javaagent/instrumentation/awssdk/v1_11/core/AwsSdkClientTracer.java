/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v1_11.core;

import com.amazonaws.Request;
import com.amazonaws.Response;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.instrumentation.api.tracer.HttpClientTracer;
import io.opentelemetry.javaagent.instrumentation.awssdk.v1_11.AwsSdkTracerSupport;
import io.opentelemetry.javaagent.instrumentation.awssdk.v1_11.RequestMeta;
import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;

public class AwsSdkClientTracer extends HttpClientTracer<Request<?>, Request<?>, Response<?>> {

  private static final AwsSdkClientTracer TRACER = new AwsSdkClientTracer();

  public static AwsSdkClientTracer tracer() {
    return TRACER;
  }

  private final AwsSdkTracerSupport awsSdkTracerSupport = new AwsSdkTracerSupport();

  public AwsSdkClientTracer() {}

  @Override
  protected String spanNameForRequest(Request<?> request) {
    return awsSdkTracerSupport.spanNameForRequest(request, DEFAULT_SPAN_NAME);
  }

  public Context startSpan(Context parentContext, Request<?> request, RequestMeta requestMeta) {
    Context context = super.startSpan(parentContext, request, request);
    awsSdkTracerSupport.onNewSpan(context, request, requestMeta);
    return context;
  }

  @Override
  public void onResponse(Span span, Response<?> response) {
    awsSdkTracerSupport.onResponse(span, response);
    super.onResponse(span, response);
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
  protected TextMapPropagator.Setter<Request<?>> getSetter() {
    return AwsSdkInjectAdapter.INSTANCE;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.aws-sdk-core";
  }

  static final class NamesCache extends ClassValue<ConcurrentHashMap<String, String>> {
    @Override
    protected ConcurrentHashMap<String, String> computeValue(Class<?> type) {
      return new ConcurrentHashMap<>();
    }
  }
}
