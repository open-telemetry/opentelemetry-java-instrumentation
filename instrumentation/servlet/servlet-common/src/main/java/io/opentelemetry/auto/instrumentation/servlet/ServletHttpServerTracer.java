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
package io.opentelemetry.auto.instrumentation.servlet;

import io.opentelemetry.auto.bootstrap.instrumentation.decorator.HttpServerTracer;
import io.opentelemetry.auto.instrumentation.api.MoreTags;
import io.opentelemetry.context.propagation.HttpTextFormat.Getter;
import io.opentelemetry.trace.Span;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.Principal;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class ServletHttpServerTracer extends HttpServerTracer<HttpServletRequest> {

  protected String getVersion() {
    return null;
  }

  @Override
  // TODO this violates convention
  protected URI url(HttpServletRequest httpServletRequest) throws URISyntaxException {
    return new URI(
        httpServletRequest.getScheme(),
        null,
        httpServletRequest.getServerName(),
        httpServletRequest.getServerPort(),
        httpServletRequest.getRequestURI(),
        httpServletRequest.getQueryString(),
        null);
  }

  @Override
  public Span getAttachedSpan(HttpServletRequest request) {
    Object span = request.getAttribute(SPAN_ATTRIBUTE);
    return span instanceof Span ? (Span) span : null;
  }

  @Override
  protected void attachSpanToRequest(Span span, HttpServletRequest request) {
    request.setAttribute(SPAN_ATTRIBUTE, span);
  }

  @Override
  protected Integer peerPort(HttpServletRequest request) {
    // HttpServletResponse doesn't have accessor for remote port prior to Servlet spec 3.0
    return null;
  }

  @Override
  protected String peerHostIP(HttpServletRequest request) {
    return request.getRemoteAddr();
  }

  @Override
  protected String method(HttpServletRequest request) {
    return request.getMethod();
  }

  public void onRequest(Span span, HttpServletRequest request) {
    // we do this e.g. so that servlet containers can use these values in their access logs
    request.setAttribute("traceId", span.getContext().getTraceId().toLowerBase16());
    request.setAttribute("spanId", span.getContext().getSpanId().toLowerBase16());

    // TODO why? they are not in semantic convention, right?
    span.setAttribute("servlet.path", request.getServletPath());
    span.setAttribute("servlet.context", request.getContextPath());
    super.onRequest(span, request);
  }

  @Override
  protected Getter<HttpServletRequest> getGetter() {
    return HttpServletRequestGetter.GETTER;
  }

  protected Throwable unwrapThrowable(Throwable throwable) {
    Throwable result = throwable;
    if (throwable instanceof ServletException && throwable.getCause() != null) {
      result = throwable.getCause();
    }
    return super.unwrapThrowable(result);
  }

  public void setPrincipal(HttpServletRequest request) {
    final Span existingSpan = getAttachedSpan(request);
    if (existingSpan != null) {
      final Principal principal = request.getUserPrincipal();
      if (principal != null) {
        existingSpan.setAttribute(MoreTags.USER_NAME, principal.getName());
      }
    }
  }
}
