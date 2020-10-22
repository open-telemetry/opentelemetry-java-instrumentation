/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import io.opentelemetry.context.propagation.TextMapPropagator.Setter;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.instrumentation.api.tracer.HttpClientTracer;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Span.Kind;
import io.opentelemetry.trace.Tracer;
import io.opentelemetry.trace.attributes.SemanticAttributes;
import java.net.URI;
import software.amazon.awssdk.http.SdkHttpHeaders;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.SdkHttpResponse;

final class AwsSdkHttpClientTracer
    extends HttpClientTracer<SdkHttpRequest, SdkHttpRequest, SdkHttpResponse> {

  static final AwsSdkHttpClientTracer TRACER = new AwsSdkHttpClientTracer();

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
    return "io.opentelemetry.auto.aws-sdk";
  }

  /** This method is overridden to allow other classes in this package to call it. */
  @Override
  protected Span onRequest(Span span, SdkHttpRequest sdkHttpRequest) {
    return super.onRequest(span, sdkHttpRequest);
  }

  public Span getOrCreateSpan(String name, Tracer tracer, Kind kind) {
    io.opentelemetry.context.Context context = io.opentelemetry.context.Context.current();
    Span clientSpan = context.getValue(BaseTracer.CONTEXT_CLIENT_SPAN_KEY);

    if (clientSpan != null) {
      // We don't want to create two client spans for a given client call, suppress inner spans.
      return Span.getInvalid();
    }

    return tracer.spanBuilder(name).setSpanKind(kind).setParent(context).startSpan();
  }
}
