/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpasyncclient;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.javaagent.instrumentation.apachehttpasyncclient.HttpHeadersInjectAdapter.SETTER;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.instrumentation.api.tracer.HttpClientTracer;
import java.net.URI;
import java.net.URISyntaxException;
import org.apache.http.Header;
import org.apache.http.HttpMessage;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.RequestLine;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpUriRequest;
import org.checkerframework.checker.nullness.qual.Nullable;

public class ApacheHttpAsyncClientTracer
    extends HttpClientTracer<HttpRequest, HttpRequest, HttpResponse> {

  private static final ApacheHttpAsyncClientTracer TRACER = new ApacheHttpAsyncClientTracer();

  public static ApacheHttpAsyncClientTracer tracer() {
    return TRACER;
  }

  public Context startSpan(Context parentContext) {
    Span span =
        tracer
            .spanBuilder(DEFAULT_SPAN_NAME)
            .setSpanKind(CLIENT)
            .setParent(parentContext)
            .startSpan();
    return withClientSpan(parentContext, span);
  }

  @Override
  protected String method(HttpRequest request) {
    if (request instanceof HttpUriRequest) {
      return ((HttpUriRequest) request).getMethod();
    } else {
      RequestLine requestLine = request.getRequestLine();
      return requestLine == null ? null : requestLine.getMethod();
    }
  }

  @Override
  protected @Nullable String flavor(HttpRequest httpRequest) {
    return httpRequest.getProtocolVersion().toString();
  }

  @Override
  protected URI url(HttpRequest request) throws URISyntaxException {
    /*
     * Note: this is essentially an optimization: HttpUriRequest allows quicker access to required information.
     * The downside is that we need to load HttpUriRequest which essentially means we depend on httpasyncclient
     * library depending on httpclient library. Currently this seems to be the case.
     */
    if (request instanceof HttpUriRequest) {
      return ((HttpUriRequest) request).getURI();
    } else {
      RequestLine requestLine = request.getRequestLine();
      return requestLine == null ? null : new URI(requestLine.getUri());
    }
  }

  @Override
  protected Integer status(HttpResponse response) {
    StatusLine statusLine = response.getStatusLine();
    return statusLine != null ? statusLine.getStatusCode() : null;
  }

  @Override
  protected String requestHeader(HttpRequest request, String name) {
    return header(request, name);
  }

  @Override
  protected String responseHeader(HttpResponse response, String name) {
    return header(response, name);
  }

  @Override
  protected TextMapSetter<HttpRequest> getSetter() {
    return SETTER;
  }

  private static String header(HttpMessage message, String name) {
    Header header = message.getFirstHeader(name);
    return header != null ? header.getValue() : null;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.apache-httpasyncclient-4.1";
  }

  /** This method is overridden to allow other classes in this package to call it. */
  @Override
  public String spanNameForRequest(HttpRequest httpRequest) {
    return super.spanNameForRequest(httpRequest);
  }

  /** This method is overridden to allow other classes in this package to call it. */
  @Override
  protected void inject(Context context, HttpRequest httpRequest) {
    super.inject(context, httpRequest);
  }

  /** This method is overridden to allow other classes in this package to call it. */
  @Override
  protected void onRequest(Span span, HttpRequest httpRequest) {
    super.onRequest(span, httpRequest);
  }
}
