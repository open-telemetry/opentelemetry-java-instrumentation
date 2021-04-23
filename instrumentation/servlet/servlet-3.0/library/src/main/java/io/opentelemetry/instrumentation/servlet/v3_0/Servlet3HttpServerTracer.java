/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.v3_0;

import static io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming.Source.SERVLET;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming;
import io.opentelemetry.instrumentation.servlet.MappingResolver;
import io.opentelemetry.instrumentation.servlet.javax.JavaxServletHttpServerTracer;
import java.util.Collection;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletRegistration;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class Servlet3HttpServerTracer extends JavaxServletHttpServerTracer<HttpServletResponse> {
  private static final Servlet3HttpServerTracer TRACER = new Servlet3HttpServerTracer();

  protected Servlet3HttpServerTracer() {
    super(Servlet3Accessor.INSTANCE);
  }

  public static Servlet3HttpServerTracer tracer() {
    return TRACER;
  }

  public Context startSpan(Object servletOrFilter, HttpServletRequest request) {
    return startSpan(
        request, getSpanName(servletOrFilter, request), servletOrFilter instanceof Servlet);
  }

  private String getSpanName(Object servletOrFilter, HttpServletRequest request) {
    String spanName = getSpanNameFromPath(servletOrFilter, request);
    if (spanName == null) {
      String contextPath = request.getContextPath();
      if (contextPath == null || contextPath.isEmpty() || contextPath.equals("/")) {
        return "HTTP " + request.getMethod();
      }
      return contextPath;
    }
    return spanName;
  }

  private static String getSpanNameFromPath(Object servletOrFilter, HttpServletRequest request) {
    // we are only interested in Servlets
    if (!(servletOrFilter instanceof Servlet)) {
      return null;
    }
    Servlet servlet = (Servlet) servletOrFilter;

    String mapping = getMapping(servlet, request.getServletPath(), request.getPathInfo());
    // mapping was not found
    if (mapping == null) {
      return null;
    }

    // prepend context path
    String contextPath = request.getContextPath();
    if (contextPath == null || contextPath.isEmpty() || contextPath.equals("/")) {
      return mapping;
    }
    return contextPath + mapping;
  }

  private static String getMapping(Servlet servlet, String servletPath, String pathInfo) {
    ServletConfig servletConfig = servlet.getServletConfig();
    if (servletConfig == null) {
      return null;
    }
    String servletName = servletConfig.getServletName();
    ServletContext servletContext = servletConfig.getServletContext();
    MappingResolver mappingResolver = getMappingResolver(servletContext, servletName);
    if (mappingResolver == null) {
      return null;
    }

    return mappingResolver.resolve(servletPath, pathInfo);
  }

  private static MappingResolver getMappingResolver(
      ServletContext servletContext, String servletName) {
    if (servletContext == null || servletName == null) {
      return null;
    }
    String key = MappingResolver.class.getName() + "." + servletName;
    MappingResolver mappingResolver = (MappingResolver) servletContext.getAttribute(key);
    if (mappingResolver != null) {
      return mappingResolver;
    }

    ServletRegistration servletRegistration = servletContext.getServletRegistration(servletName);
    if (servletRegistration == null) {
      return null;
    }
    Collection<String> mappings = servletRegistration.getMappings();
    if (mappings == null) {
      return null;
    }

    mappingResolver = MappingResolver.build(mappings);
    servletContext.setAttribute(key, mappingResolver);

    return mappingResolver;
  }

  public Context updateContext(
      Context context, Object servletOrFilter, HttpServletRequest request) {
    ServerSpanNaming.updateServerSpanName(
        context, SERVLET, () -> getSpanNameFromPath(servletOrFilter, request));
    return updateContext(context, request);
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.servlet-3.0";
  }
}
