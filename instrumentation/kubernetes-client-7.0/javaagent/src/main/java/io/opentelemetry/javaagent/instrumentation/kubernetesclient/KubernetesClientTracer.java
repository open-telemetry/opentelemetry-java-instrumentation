/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kubernetesclient;

import static io.opentelemetry.api.trace.Span.Kind.CLIENT;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator.Setter;
import io.opentelemetry.instrumentation.api.instrumenter.HttpClientInstrumenter;
import io.opentelemetry.instrumentation.api.tracer.Tracer;
import java.net.URI;
import okhttp3.Request;
import okhttp3.Response;

public class KubernetesClientTracer extends HttpClientInstrumenter<Request, Request, Response> {
  private static final KubernetesClientTracer TRACER = new KubernetesClientTracer();

  public static KubernetesClientTracer tracer() {
    return TRACER;
  }

  /**
   * This method is used to generate an acceptable CLIENT span (operation) name based on a given
   * KubernetesRequestDigest.
   */
  public Context startSpan(Context parentContext, Request request) {
    KubernetesRequestDigest digest = KubernetesRequestDigest.parse(request);
    Span span =
        tracer
            .spanBuilder(digest.toString())
            .setSpanKind(CLIENT)
            .setParent(parentContext)
            .setAttribute("namespace", digest.getResourceMeta().getNamespace())
            .setAttribute("name", digest.getResourceMeta().getName())
            .startSpan();
    Context context = parentContext.with(span).with(Tracer.CONTEXT_CLIENT_SPAN_KEY, span);
    OpenTelemetry.getGlobalPropagators()
        .getTextMapPropagator()
        .inject(context, request, getSetter());
    return context;
  }

  @Override
  protected String method(Request httpRequest) {
    return httpRequest.method();
  }

  @Override
  protected URI url(Request httpRequest) {
    return httpRequest.url().uri();
  }

  @Override
  protected Integer status(Response httpResponse) {
    return httpResponse.code();
  }

  @Override
  protected String requestHeader(Request request, String name) {
    return request.header(name);
  }

  @Override
  protected String responseHeader(Response response, String name) {
    return response.header(name);
  }

  @Override
  protected Setter<Request> getSetter() {
    // TODO (trask) no propagation implemented yet?
    return null;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.kubernetes-client";
  }

  /** This method is overridden to allow other classes in this package to call it. */
  @Override
  protected Span onRequest(Span span, Request request) {
    return super.onRequest(span, request);
  }
}
