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

  public Context startOperation(Context parentContext, ExecutionAttributes attributes) {
    if (inClientSpan(parentContext)) {
      return noopContext(parentContext);
    }
    String spanName = spanName(attributes);
    Span span =
        tracer.spanBuilder(spanName).setSpanKind(CLIENT).setParent(parentContext).startSpan();
    return withClientSpan(parentContext, span);
  }

  @Override
  public void onRequest(Context context, SdkHttpRequest request) {
    super.onRequest(context, request);
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

  // TODO (trask) is there more consistent way to handle this?
  public void inject(Context context, SdkHttpRequest.Builder builder) {
    AwsXRayPropagator.getInstance().inject(context, builder, AwsSdkInjectAdapter.INSTANCE);
  }

  private static String spanName(ExecutionAttributes attributes) {
    String awsServiceName = attributes.getAttribute(SdkExecutionAttribute.SERVICE_NAME);
    String awsOperation = attributes.getAttribute(SdkExecutionAttribute.OPERATION_NAME);
    return awsServiceName + "." + awsOperation;
  }
}
