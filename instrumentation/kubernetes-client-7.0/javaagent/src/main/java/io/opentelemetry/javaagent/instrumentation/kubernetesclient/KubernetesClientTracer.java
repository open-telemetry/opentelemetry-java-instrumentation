/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kubernetesclient;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.ApiResponse;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.tracer.HttpClientTracer;
import io.opentelemetry.javaagent.instrumentation.okhttp.v3_0.RequestBuilderInjectAdapter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import okhttp3.Request;

public class KubernetesClientTracer
    extends HttpClientTracer<Request, Request.Builder, ApiResponse<?>> {

  private static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      Config.get()
          .getBooleanProperty(
              "otel.instrumentation.kubernetes-client.experimental-span-attributes", false);

  private static final KubernetesClientTracer TRACER = new KubernetesClientTracer();

  public static KubernetesClientTracer tracer() {
    return TRACER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.kubernetes-client-7.0";
  }

  @Override
  protected String method(Request request) {
    return request.method();
  }

  @Override
  protected URI url(Request request) {
    return request.url().uri();
  }

  @Override
  protected Integer status(ApiResponse<?> response) {
    return response.getStatusCode();
  }

  @Override
  protected String requestHeader(Request request, String name) {
    return request.header(name);
  }

  @Override
  protected String responseHeader(ApiResponse<?> response, String name) {
    Map<String, List<String>> responseHeaders =
        response.getHeaders() == null ? Collections.emptyMap() : response.getHeaders();
    return responseHeaders.getOrDefault(name, Collections.emptyList()).stream()
        .findFirst()
        .orElse(null);
  }

  @Override
  protected TextMapSetter<Request.Builder> getSetter() {
    return RequestBuilderInjectAdapter.SETTER;
  }

  @Override
  protected void onError(Span span, Throwable throwable) {
    super.onError(span, throwable);
    if (throwable instanceof ApiException) {
      int status = ((ApiException) throwable).getCode();
      if (status != 0) {
        span.setAttribute(SemanticAttributes.HTTP_STATUS_CODE, status);
      }
    }
  }

  @Override
  protected void onRequest(Span span, Request request) {
    super.onRequest(span, request);
    if (CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
      KubernetesRequestDigest digest = KubernetesRequestDigest.parse(request);
      span.setAttribute("kubernetes-client.namespace", digest.getResourceMeta().getNamespace())
          .setAttribute("kubernetes-client.name", digest.getResourceMeta().getName());
    }
  }

  @Override
  protected String spanNameForRequest(Request request) {
    return KubernetesRequestDigest.parse(request).toString();
  }
}
