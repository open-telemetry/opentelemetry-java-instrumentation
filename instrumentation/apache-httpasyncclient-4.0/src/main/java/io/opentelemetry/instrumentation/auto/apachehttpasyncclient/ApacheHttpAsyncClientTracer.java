/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.instrumentation.auto.apachehttpasyncclient;

import static io.opentelemetry.instrumentation.auto.apachehttpasyncclient.HttpHeadersInjectAdapter.SETTER;

import io.opentelemetry.context.propagation.HttpTextFormat.Setter;
import io.opentelemetry.instrumentation.api.tracer.HttpClientTracer;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Span.Kind;
import java.net.URI;
import java.net.URISyntaxException;
import org.apache.http.Header;
import org.apache.http.HttpMessage;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.RequestLine;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpUriRequest;

public class ApacheHttpAsyncClientTracer extends HttpClientTracer<HttpRequest, HttpResponse> {

  public static final ApacheHttpAsyncClientTracer TRACER = new ApacheHttpAsyncClientTracer();

  @Override
  protected String method(final HttpRequest request) {
    if (request instanceof HttpUriRequest) {
      return ((HttpUriRequest) request).getMethod();
    } else {
      RequestLine requestLine = request.getRequestLine();
      return requestLine == null ? null : requestLine.getMethod();
    }
  }

  @Override
  protected URI url(final HttpRequest request) throws URISyntaxException {
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
  protected Integer status(final HttpResponse response) {
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
  protected Setter<HttpRequest> getSetter() {
    return SETTER;
  }

  private static String header(HttpMessage message, String name) {
    Header header = message.getFirstHeader(name);
    return header != null ? header.getValue() : null;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.apache-httpasyncclient-4.0";
  }

  /**
   * This method is used to generate an acceptable CLIENT span (operation) name based on a given
   * name.
   */
  @Override
  public Span startSpan(String spanName) {
    return tracer.spanBuilder(spanName).setSpanKind(Kind.CLIENT).startSpan();
  }

  @Override
  public String spanNameForRequest(HttpRequest httpRequest) {
    return super.spanNameForRequest(httpRequest);
  }

  /** This method is overridden to allow other classes in this package to call it. */
  @Override
  public Span onRequest(Span span, HttpRequest httpRequest) {
    return super.onRequest(span, httpRequest);
  }
}
