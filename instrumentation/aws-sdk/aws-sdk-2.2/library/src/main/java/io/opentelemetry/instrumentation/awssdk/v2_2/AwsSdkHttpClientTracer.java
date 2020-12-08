/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Span.Kind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.attributes.SemanticAttributes;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator.Setter;
import io.opentelemetry.instrumentation.api.tracer.HttpClientTracer;
import java.net.URI;
import software.amazon.awssdk.http.SdkHttpHeaders;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.SdkHttpResponse;

final class AwsSdkHttpClientTracer
    extends HttpClientTracer<SdkHttpRequest, SdkHttpRequest, SdkHttpResponse> {

  private static final AwsSdkHttpClientTracer TRACER = new AwsSdkHttpClientTracer();

  static AwsSdkHttpClientTracer tracer() {
    return TRACER;
  }

  public AwsSdkOperation startOperation(String name, Tracer tracer, Kind kind) {
    Context parentContext = Context.current();
    if (!shouldStartSpan(parentContext)) {
      return AwsSdkOperation.noop();
    }
    Span clientSpan =
        tracer.spanBuilder(name).setSpanKind(kind).setParent(parentContext).startSpan();
    Context context = withClientSpan(parentContext, clientSpan);
    return new DefaultAwsSdkOperation(context, parentContext);
  }

  // Certain headers in the request like User-Agent are only available after execution.
  Span afterExecution(Span span, SdkHttpRequest request) {
    span.setAttribute(SemanticAttributes.HTTP_USER_AGENT, requestHeader(request, USER_AGENT));
    return span;
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
  protected Setter<SdkHttpRequest> getSetter() {
    return null;
  }

  private static String header(SdkHttpHeaders headers, String name) {
    return headers.firstMatchingHeader(name).orElse(null);
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.aws-sdk";
  }

  void onRequest(Span span, SdkHttpRequest sdkHttpRequest) {
    super.onRequest(span::setAttribute, sdkHttpRequest);
  }
}
