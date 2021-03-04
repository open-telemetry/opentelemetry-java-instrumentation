/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.extension.aws.AwsXrayPropagator;
import io.opentelemetry.instrumentation.api.tracer.HttpClientTracer;
import java.net.URI;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.SdkExecutionAttribute;
import software.amazon.awssdk.http.SdkHttpHeaders;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.SdkHttpResponse;

final class AwsSdkHttpClientTracer
    extends HttpClientTracer<SdkHttpRequest, SdkHttpRequest.Builder, SdkHttpResponse> {

  AwsSdkHttpClientTracer(OpenTelemetry openTelemetry) {
    super(openTelemetry);
  }

  public Context startSpan(Context parentContext, ExecutionAttributes attributes) {
    String spanName = spanName(attributes);
    Span span =
        tracer.spanBuilder(spanName).setSpanKind(CLIENT).setParent(parentContext).startSpan();
    return withClientSpan(parentContext, span);
  }

  public void inject(Context context, SdkHttpRequest.Builder builder) {
    AwsXrayPropagator.getInstance().inject(context, builder, getSetter());
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

  @Override
  protected TextMapSetter<SdkHttpRequest.Builder> getSetter() {
    return AwsSdkInjectAdapter.INSTANCE;
  }

  private static String header(SdkHttpHeaders headers, String name) {
    return headers.firstMatchingHeader(name).orElse(null);
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.aws-sdk-2.2";
  }

  /** This method is overridden to allow other classes in this package to call it. */
  @Override
  protected void onRequest(Span span, SdkHttpRequest sdkHttpRequest) {
    super.onRequest(span, sdkHttpRequest);
  }

  private static String spanName(ExecutionAttributes attributes) {
    String awsServiceName = attributes.getAttribute(SdkExecutionAttribute.SERVICE_NAME);
    String awsOperation = attributes.getAttribute(SdkExecutionAttribute.OPERATION_NAME);
    return awsServiceName + "." + awsOperation;
  }
}
