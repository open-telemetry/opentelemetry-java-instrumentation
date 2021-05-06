/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.naming;

import io.opentelemetry.instrumentation.api.servlet.MappingResolver;
import io.opentelemetry.instrumentation.servlet.ServletAccessor;

/** Helper class for constructing span name for given servlet/filter mapping and request. */
public class ServletSpanNameProvider<REQUEST> {
  private final ServletAccessor<REQUEST, ?> servletAccessor;

  public ServletSpanNameProvider(ServletAccessor<REQUEST, ?> servletAccessor) {
    this.servletAccessor = servletAccessor;
  }

  public String getSpanName(MappingResolver mappingResolver, REQUEST request) {
    String spanName = getSpanNameOrNull(mappingResolver, request);
    if (spanName == null) {
      String contextPath = servletAccessor.getRequestContextPath(request);
      if (contextPath == null || contextPath.isEmpty() || contextPath.equals("/")) {
        return "HTTP " + servletAccessor.getRequestMethod(request);
      }
      return contextPath + "/*";
    }
    return spanName;
  }

  public String getSpanNameOrNull(MappingResolver mappingResolver, REQUEST request) {
    if (mappingResolver == null) {
      return null;
    }

    String servletPath = servletAccessor.getRequestServletPath(request);
    String pathInfo = servletAccessor.getRequestPathInfo(request);
    String mapping = mappingResolver.resolve(servletPath, pathInfo);
    // mapping was not found
    if (mapping == null) {
      return null;
    }

    // prepend context path
    String contextPath = servletAccessor.getRequestContextPath(request);
    if (contextPath == null || contextPath.isEmpty() || contextPath.equals("/")) {
      return mapping;
    }
    return contextPath + mapping;
  }
}
