/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapPropagator.Setter;
import io.opentelemetry.extension.trace.propagation.AwsXRayPropagator;
import io.opentelemetry.instrumentation.api.tracer.LazyHttpClientTracer;
import java.net.URI;
import software.amazon.awssdk.http.SdkHttpHeaders;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.SdkHttpResponse;

final class AwsSdkHttpClientTracer
    extends LazyHttpClientTracer<SdkHttpRequest, SdkHttpRequest.Builder, SdkHttpResponse> {

  private static final AwsSdkHttpClientTracer TRACER = new AwsSdkHttpClientTracer();

  static AwsSdkHttpClientTracer tracer() {
    return TRACER;
  }

  @Override
  protected TextMapPropagator getTextMapPropagator() {
    return AwsXRayPropagator.getInstance();
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
  protected Setter<SdkHttpRequest.Builder> getSetter() {
    return AwsSdkInjectAdapter.INSTANCE;
  }

  private static String header(SdkHttpHeaders headers, String name) {
    return headers.firstMatchingHeader(name).orElse(null);
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.aws-sdk";
  }
}
