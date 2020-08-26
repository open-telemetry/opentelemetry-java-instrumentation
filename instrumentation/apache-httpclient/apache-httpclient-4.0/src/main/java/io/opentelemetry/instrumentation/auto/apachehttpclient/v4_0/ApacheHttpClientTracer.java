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

package io.opentelemetry.instrumentation.auto.apachehttpclient.v4_0;

import static io.opentelemetry.instrumentation.auto.apachehttpclient.v4_0.HttpHeadersInjectAdapter.SETTER;

import io.opentelemetry.context.propagation.TextMapPropagator.Setter;
import io.opentelemetry.instrumentation.api.tracer.HttpClientTracer;
import io.opentelemetry.trace.Span;
import java.net.URI;
import org.apache.http.Header;
import org.apache.http.HttpMessage;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpUriRequest;

class ApacheHttpClientTracer
    extends HttpClientTracer<HttpUriRequest, HttpUriRequest, HttpResponse> {

  public static final ApacheHttpClientTracer TRACER = new ApacheHttpClientTracer();

  @Override
  protected String method(HttpUriRequest httpRequest) {
    return httpRequest.getMethod();
  }

  @Override
  protected URI url(HttpUriRequest request) {
    return request.getURI();
  }

  @Override
  protected Integer status(HttpResponse httpResponse) {
    return httpResponse.getStatusLine().getStatusCode();
  }

  @Override
  protected String requestHeader(HttpUriRequest request, String name) {
    return header(request, name);
  }

  @Override
  protected String responseHeader(HttpResponse response, String name) {
    return header(response, name);
  }

  @Override
  protected Setter<HttpUriRequest> getSetter() {
    return SETTER;
  }

  private static String header(HttpMessage message, String name) {
    Header header = message.getFirstHeader(name);
    return header != null ? header.getValue() : null;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.apache-httpclient-4.0";
  }

  /** This method is overridden to allow other classes in this package to call it. */
  @Override
  protected Span onResponse(Span span, HttpResponse httpResponse) {
    return super.onResponse(span, httpResponse);
  }
}
