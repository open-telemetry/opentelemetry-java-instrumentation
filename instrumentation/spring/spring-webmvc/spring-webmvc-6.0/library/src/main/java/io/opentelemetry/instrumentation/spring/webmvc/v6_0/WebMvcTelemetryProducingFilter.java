/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webmvc.v6_0;

import static io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerRouteSource.CONTROLLER;
import static java.util.Objects.requireNonNull;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerRoute;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

final class WebMvcTelemetryProducingFilter extends OncePerRequestFilter implements Ordered {

  private final Instrumenter<HttpServletRequest, HttpServletResponse> instrumenter;
  private final HttpRouteSupport httpRouteSupport = new HttpRouteSupport();

  WebMvcTelemetryProducingFilter(
      Instrumenter<HttpServletRequest, HttpServletResponse> instrumenter) {
    this.instrumenter = instrumenter;
  }

  @Override
  public void afterPropertiesSet() {
    // don't do anything, in particular do not call initFilterBean()
  }

  @Override
  protected void initFilterBean() {
    // FilterConfig must be non-null at this point
    httpRouteSupport.onFilterInit(requireNonNull(getFilterConfig()));
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
    Throwable error = null;
    try (Scope ignored = context.makeCurrent()) {
      filterChain.doFilter(request, response);
    } catch (Throwable t) {
      error = t;
      throw t;
    } finally {
      if (httpRouteSupport.hasMappings()) {
        HttpServerRoute.update(context, CONTROLLER, httpRouteSupport::getHttpRoute, request);
      }
      instrumenter.end(context, request, response, error);
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
