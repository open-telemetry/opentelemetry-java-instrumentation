/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webmvc;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

final class WebMvcTracingFilter extends OncePerRequestFilter implements Ordered {

  private final Instrumenter<HttpServletRequest, HttpServletResponse> instrumenter;

  WebMvcTracingFilter(Instrumenter<HttpServletRequest, HttpServletResponse> instrumenter) {
    this.instrumenter = instrumenter;
  }

  @Override
  public void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    Context parentContext = Context.current();
    if (!instrumenter.shouldStart(parentContext, request)) {
      filterChain.doFilter(request, response);
      return;
    }

    Context context = instrumenter.start(parentContext, request);
    try (Scope ignored = context.makeCurrent()) {
      filterChain.doFilter(request, response);
      instrumenter.end(context, request, response, null);
    } catch (Throwable t) {
      instrumenter.end(context, request, response, t);
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
