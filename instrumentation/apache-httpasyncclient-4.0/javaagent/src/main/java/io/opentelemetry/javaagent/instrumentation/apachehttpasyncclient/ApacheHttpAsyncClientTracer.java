/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpasyncclient;

import static io.opentelemetry.api.trace.Span.Kind.CLIENT;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.HttpClientTracer;
import io.opentelemetry.instrumentation.api.tracer.Operation;
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

public class ApacheHttpAsyncClientTracer extends HttpClientTracer<HttpRequest, HttpResponse> {

  private static final ApacheHttpAsyncClientTracer TRACER = new ApacheHttpAsyncClientTracer();

  public static ApacheHttpAsyncClientTracer tracer() {
    return TRACER;
  }

  public final Operation<HttpResponse> startOperation() {
    Context parentContext = Context.current();
    if (inClientSpan(parentContext)) {
      return Operation.noop();
    }
    Span clientSpan =
        tracer
            .spanBuilder(DEFAULT_SPAN_NAME)
            .setSpanKind(CLIENT)
            .setParent(parentContext)
            .startSpan();
    Context context = withClientSpan(parentContext, clientSpan);
    return Operation.create(context, parentContext, this);
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
  public void onRequest(Span span, HttpRequest request) {
    String method = method(request);
    if (method != null) {
      span.updateName("HTTP " + method);
    }
    super.onRequest(span, request);
  }

  private static String header(HttpMessage message, String name) {
    Header header = message.getFirstHeader(name);
    return header != null ? header.getValue() : null;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.apache-httpasyncclient";
  }
}
