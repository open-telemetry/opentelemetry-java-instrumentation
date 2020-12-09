/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webmvc;

import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

public class WebMvcTracingFilter extends OncePerRequestFilter implements Ordered {

  private static final String FILTER_CLASS = "WebMVCTracingFilter";
  private static final String FILTER_METHOD = "doFilterInteral";
  private final SpringWebMvcServerInstrumenter instrumenter;

  public WebMvcTracingFilter(Tracer instrumenter) {
    this.instrumenter = new SpringWebMvcServerInstrumenter(instrumenter);
  }

  @Override
  public void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    Context ctx =
        instrumenter.startOperation(request, request, request, FILTER_CLASS + "." + FILTER_METHOD);
    try (Scope ignored = ctx.makeCurrent()) {
      filterChain.doFilter(request, response);
      instrumenter.end(ctx, response);
    } catch (Throwable t) {
      instrumenter.endExceptionally(ctx, t, response);
      throw t;
    }
  }

  @Override
  public void destroy() {}

  @Override
  public int getOrder() {
    // Run after all HIGHEST_PRECEDENCE items
    return Ordered.HIGHEST_PRECEDENCE + 1;
  }
}
