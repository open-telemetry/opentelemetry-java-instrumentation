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

package io.opentelemetry.auto.instrumentation.grizzly;

import static io.opentelemetry.auto.instrumentation.grizzly.GrizzlyRequestExtractAdapter.GETTER;

import io.opentelemetry.auto.bootstrap.instrumentation.decorator.HttpServerTracer;
import io.opentelemetry.context.propagation.HttpTextFormat.Getter;
import io.opentelemetry.trace.Span;
import java.net.URI;
import java.net.URISyntaxException;
import org.glassfish.grizzly.http.server.Request;

public class GrizzlyHttpServerTracer extends HttpServerTracer<Request> {
  public static final GrizzlyHttpServerTracer TRACER = new GrizzlyHttpServerTracer();

  @Override
  protected String getVersion() {
    return null;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.grizzly-2.0";
  }

  @Override
  protected Integer peerPort(Request request) {
    return request.getRemotePort();
  }

  @Override
  protected String peerHostIP(Request request) {
    return request.getRemoteAddr();
  }

  @Override
  protected void attachSpanToRequest(Span span, Request request) {
    request.setAttribute(SPAN_ATTRIBUTE, span);
  }

  @Override
  protected URI url(Request request) throws URISyntaxException {
    return new URI(
        request.getScheme(),
        null,
        request.getServerName(),
        request.getServerPort(),
        request.getRequestURI(),
        request.getQueryString(),
        null);
  }

  @Override
  protected String method(Request request) {
    return request.getMethod().getMethodString();
  }

  @Override
  protected Getter<Request> getGetter() {
    return GETTER;
  }

  @Override
  public Span getAttachedSpan(Request request) {
    Object span = request.getAttribute(SPAN_ATTRIBUTE);
    return span instanceof Span ? (Span) span : null;
  }

  @Override
  public void onRequest(Span span, Request request) {
    request.setAttribute("traceId", span.getContext().getTraceId().toLowerBase16());
    request.setAttribute("spanId", span.getContext().getSpanId().toLowerBase16());
    super.onRequest(span, request);
  }
}
