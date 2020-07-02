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
package io.opentelemetry.spring.instrumentation.trace.web;

import io.grpc.Context;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.HttpTextFormat;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import io.opentelemetry.trace.TracingContextUtils;
import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

/**
 * Wraps requests to servlet or resource in a span. Gets the parent span context from request
 * headers.
 */
public final class WebMVCFilter implements Filter {

  private static final HttpTextFormat.Getter<HttpServletRequest> GETTER =
      new HttpTextFormat.Getter<HttpServletRequest>() {
        @Override
        public String get(HttpServletRequest req, String key) {
          return req.getHeader(key);
        }
      };

  private final Tracer tracer;

  public WebMVCFilter(Tracer tracer) {
    this.tracer = tracer;
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {

    HttpServletRequest req = (HttpServletRequest) request;
    Context context =
        OpenTelemetry.getPropagators().getHttpTextFormat().extract(Context.current(), req, GETTER);
    Span currentSpan = createSpanWithParent(req, context);

    try (Scope scope = tracer.withSpan(currentSpan)) {
      chain.doFilter(req, response);
    } finally {
      currentSpan.end();
    }
  }

  private Span createSpanWithParent(HttpServletRequest request, Context context) {
    Span parentSpan = TracingContextUtils.getSpan(context);
    Span.Builder spanBuilder =
        tracer.spanBuilder(request.getRequestURI()).setSpanKind(Span.Kind.SERVER);

    if (parentSpan.getContext().isValid()) {
      return spanBuilder.setParent(parentSpan).startSpan();
    }

    Span span = spanBuilder.startSpan();
    span.addEvent("Parent Span Not Found");

    return span;
  }
}
