/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.naming;

import io.opentelemetry.instrumentation.servlet.ServletAccessor;
import java.util.Collection;

/** Helper class for constructing span name for given servlet/filter and request. */
public abstract class ServletSpanNameProvider<SERVLETCONTEXT, REQUEST> {
  private final ServletAccessor<SERVLETCONTEXT, REQUEST, ?> servletAccessor;

  public ServletSpanNameProvider(ServletAccessor<SERVLETCONTEXT, REQUEST, ?> servletAccessor) {
    this.servletAccessor = servletAccessor;
  }

  public String getSpanName(Object servletOrFilter, REQUEST request) {
    String spanName = getSpanNameOrNull(servletOrFilter, request);
    if (spanName == null) {
      String contextPath = servletAccessor.getRequestContextPath(request);
      if (contextPath == null || contextPath.isEmpty() || contextPath.equals("/")) {
        return "HTTP " + servletAccessor.getRequestMethod(request);
      }
      return contextPath;
    }
    return spanName;
  }

  public String getSpanNameOrNull(Object servletOrFilter, REQUEST request) {
    String mapping =
        getMapping(
            getMappingProvider(servletOrFilter),
            servletAccessor.getRequestServletPath(request),
            servletAccessor.getRequestPathInfo(request));
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

  protected abstract MappingProvider<SERVLETCONTEXT> getMappingProvider(Object servletOrFilter);

  private String getMapping(MappingProvider mappingProvider, String servletPath, String pathInfo) {
    if (mappingProvider == null) {
      return null;
    }
    MappingResolver mappingResolver = getMappingResolver(mappingProvider);
    if (mappingResolver == null) {
      return null;
    }

    return mappingResolver.resolve(servletPath, pathInfo);
  }

  private MappingResolver getMappingResolver(MappingProvider<SERVLETCONTEXT> mappingProvider) {
    SERVLETCONTEXT servletContext = mappingProvider.getServletContext();
    String key = MappingResolver.class.getName() + "." + mappingProvider.getMappingKey();
    MappingResolver mappingResolver =
        (MappingResolver) servletAccessor.getServletContextAttribute(servletContext, key);
    if (mappingResolver != null) {
      return mappingResolver;
    }

    Collection<String> mappings = mappingProvider.getMappings();
    if (mappings == null) {
      return null;
    }

    mappingResolver = MappingResolver.build(mappings);
    servletAccessor.setServletContextAttribute(servletContext, key, mappingResolver);

    return mappingResolver;
  }
}
