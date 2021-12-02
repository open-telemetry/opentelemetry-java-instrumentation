/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.servlet.MappingResolver;
import java.util.function.Function;
import javax.annotation.Nullable;

public class ServletSpanNameExtractor<REQUEST, RESPONSE>
    implements SpanNameExtractor<ServletRequestContext<REQUEST>> {
  private final ServletAccessor<REQUEST, RESPONSE> accessor;

  @Nullable
  private final Function<ServletRequestContext<REQUEST>, MappingResolver> mappingResolverFunction;

  public ServletSpanNameExtractor(
      ServletAccessor<REQUEST, RESPONSE> accessor,
      @Nullable Function<ServletRequestContext<REQUEST>, MappingResolver> mappingResolverFunction) {
    this.accessor = accessor;
    this.mappingResolverFunction = mappingResolverFunction;
  }

  @Nullable
  private String route(ServletRequestContext<REQUEST> requestContext) {
    if (mappingResolverFunction == null) {
      return null;
    }
    MappingResolver mappingResolver = mappingResolverFunction.apply(requestContext);
    if (mappingResolver == null) {
      return null;
    }

    REQUEST request = requestContext.request();
    String servletPath = accessor.getRequestServletPath(request);
    String pathInfo = accessor.getRequestPathInfo(request);
    String contextPath = accessor.getRequestContextPath(request);
    boolean hasContextPath =
        contextPath != null && !contextPath.isEmpty() && !contextPath.equals("/");

    String route = mappingResolver.resolve(servletPath, pathInfo);
    if (route == null) {
      if (hasContextPath) {
        return contextPath + "/*";
      }
      return null;
    }
    // prepend context path
    return contextPath + route;
  }

  @Override
  public String extract(ServletRequestContext<REQUEST> requestContext) {
    String route = route(requestContext);
    if (route != null) {
      return route;
    }
    REQUEST request = requestContext.request();
    String method = accessor.getRequestMethod(request);
    if (method != null) {
      return "HTTP " + method;
    }
    return "HTTP request";
  }
}
