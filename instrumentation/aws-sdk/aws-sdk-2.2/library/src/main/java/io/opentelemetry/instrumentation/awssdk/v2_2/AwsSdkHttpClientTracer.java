/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import static io.opentelemetry.api.trace.Span.Kind.CLIENT;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.extension.trace.propagation.AwsXRayPropagator;
import io.opentelemetry.instrumentation.api.tracer.HttpClientTracer;
import io.opentelemetry.instrumentation.api.tracer.Operation;
import java.net.URI;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.SdkExecutionAttribute;
import software.amazon.awssdk.http.SdkHttpHeaders;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.SdkHttpResponse;

final class AwsSdkHttpClientTracer extends HttpClientTracer<SdkHttpRequest, SdkHttpResponse> {

  private static final AwsSdkHttpClientTracer TRACER = new AwsSdkHttpClientTracer();

  static AwsSdkHttpClientTracer tracer() {
    return TRACER;
  }

  public final Operation startOperation(ExecutionAttributes attributes) {
    Context parentContext = Context.current();
    if (inClientSpan(parentContext)) {
      return Operation.noop();
    }
    String spanName = spanName(attributes);
    Span span =
        tracer.spanBuilder(spanName).setSpanKind(CLIENT).setParent(parentContext).startSpan();
    Context context = withClientSpan(parentContext, span);
    return Operation.create(context, parentContext);
  }

  @Override
  public void onRequest(Operation operation, SdkHttpRequest request) {
    super.onRequest(operation, request);
  }

  @Override
  protected String method(SdkHttpRequest request) {
    return request.method().name();
  }

  @Override
  protected URI url(SdkHttpRequest request) {
    return request.getUri();
  }

  @Override
  protected Integer status(SdkHttpResponse response) {
    return response.statusCode();
  }

  @Override
  protected String requestHeader(SdkHttpRequest sdkHttpRequest, String name) {
    return header(sdkHttpRequest, name);
  }

  @Override
  protected String responseHeader(SdkHttpResponse sdkHttpResponse, String name) {
    return header(sdkHttpResponse, name);
  }

  private static String header(SdkHttpHeaders headers, String name) {
    return headers.firstMatchingHeader(name).orElse(null);
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.aws-sdk";
  }

  public void inject(Operation operation, SdkHttpRequest.Builder builder) {
    operation.inject(AwsXRayPropagator.getInstance(), builder, AwsSdkInjectAdapter.INSTANCE);
  }

  private static String spanName(ExecutionAttributes attributes) {
    String awsServiceName = attributes.getAttribute(SdkExecutionAttribute.SERVICE_NAME);
    String awsOperation = attributes.getAttribute(SdkExecutionAttribute.OPERATION_NAME);
    return awsServiceName + "." + awsOperation;
  }
}
