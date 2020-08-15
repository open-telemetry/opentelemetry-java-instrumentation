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

package io.opentelemetry.instrumentation.auto.httpurlconnection;

import static io.opentelemetry.instrumentation.auto.httpurlconnection.HeadersInjectAdapter.SETTER;

import io.opentelemetry.context.propagation.HttpTextFormat.Setter;
import io.opentelemetry.instrumentation.api.tracer.HttpClientTracer;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Span.Kind;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;

public class HttpUrlConnectionTracer
    extends HttpClientTracer<HttpURLConnection, HttpURLConnection, Integer> {

  public static final HttpUrlConnectionTracer TRACER = new HttpUrlConnectionTracer();

  @Override
  protected String method(final HttpURLConnection connection) {
    return connection.getRequestMethod();
  }

  @Override
  protected URI url(final HttpURLConnection connection) throws URISyntaxException {
    return connection.getURL().toURI();
  }

  @Override
  protected Integer status(final Integer status) {
    return status;
  }

  @Override
  protected String requestHeader(HttpURLConnection httpURLConnection, String name) {
    return httpURLConnection.getRequestProperty(name);
  }

  @Override
  protected String responseHeader(Integer integer, String name) {
    return null;
  }

  @Override
  protected Setter<HttpURLConnection> getSetter() {
    return SETTER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.http-url-connection";
  }

  /**
   * This method is used to generate an acceptable CLIENT span (operation) name based on a given
   * name.
   */
  @Override
  public Span startSpan(String spanName) {
    return tracer.spanBuilder(spanName).setSpanKind(Kind.CLIENT).startSpan();
  }
}
