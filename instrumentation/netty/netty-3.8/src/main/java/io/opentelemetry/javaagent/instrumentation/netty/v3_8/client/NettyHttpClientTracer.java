/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.client;

import static io.opentelemetry.javaagent.instrumentation.netty.v3_8.client.NettyResponseInjectAdapter.SETTER;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.HOST;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator.Setter;
import io.opentelemetry.instrumentation.api.tracer.HttpClientTracer;
import io.opentelemetry.trace.Span;
import java.net.URI;
import java.net.URISyntaxException;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;

public class NettyHttpClientTracer
    extends HttpClientTracer<HttpRequest, HttpHeaders, HttpResponse> {
  public static final NettyHttpClientTracer TRACER = new NettyHttpClientTracer();

  @Override
  public Scope startScope(Span span, HttpHeaders headers) {
    if (!headers.contains("amz-sdk-invocation-id")) {
      return super.startScope(span, headers);
    } else {
      // TODO (trask) if we move injection up to aws-sdk layer, and start suppressing nested netty
      //  spans, do we still need this condition?
      // AWS calls are often signed, so we can't add headers without breaking the signature.
      Context context = Context.current().with(span);
      context = context.withValues(CONTEXT_CLIENT_SPAN_KEY, span);
      return context.makeCurrent();
    }
  }

  @Override
  protected String method(HttpRequest httpRequest) {
    return httpRequest.getMethod().getName();
  }

  @Override
  protected @Nullable String flavor(HttpRequest httpRequest) {
    return httpRequest.getProtocolVersion().getText();
  }

  @Override
  protected URI url(HttpRequest request) throws URISyntaxException {
    URI uri = new URI(request.getUri());
    if ((uri.getHost() == null || uri.getHost().equals("")) && request.headers().contains(HOST)) {
      return new URI("http://" + request.headers().get(HOST) + request.getUri());
    } else {
      return uri;
    }
  }

  @Override
  protected Integer status(HttpResponse httpResponse) {
    return httpResponse.getStatus().getCode();
  }

  @Override
  protected String requestHeader(HttpRequest httpRequest, String name) {
    return httpRequest.headers().get(name);
  }

  @Override
  protected String responseHeader(HttpResponse httpResponse, String name) {
    return httpResponse.headers().get(name);
  }

  @Override
  protected Setter<HttpHeaders> getSetter() {
    return SETTER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.netty";
  }
}
