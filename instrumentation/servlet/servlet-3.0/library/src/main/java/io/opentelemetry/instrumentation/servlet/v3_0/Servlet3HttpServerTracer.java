/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.v3_0;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.servlet.ServletSpanNaming;
import io.opentelemetry.instrumentation.api.tracer.ServerSpan;
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
    Context context = startSpan(request, getSpanName(servletOrFilter, request, false));
    // server span name shouldn't be update when server span was created from a call to Servlet
    // if server span was created from a call to Filter then name may be updated from updateContext.
    if (servletOrFilter instanceof Servlet) {
      ServletSpanNaming.setServletUpdatedServerSpanName(context);
    }
    return context;
  }

  private String getSpanName(
      Object servletOrFilter, HttpServletRequest request, boolean allowNull) {
    String spanName = getSpanName(servletOrFilter, request);
    if (spanName == null && !allowNull) {
      String contextPath = request.getContextPath();
      if (contextPath == null || contextPath.isEmpty() || contextPath.equals("/")) {
        return "HTTP " + request.getMethod();
      }
      return contextPath;
    }
    return spanName;
  }

  private static String getSpanName(Object servletOrFilter, HttpServletRequest request) {
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
    Span span = ServerSpan.fromContextOrNull(context);
    if (span != null && ServletSpanNaming.shouldUpdateServerSpanName(context)) {
      String spanName = getSpanName(servletOrFilter, request, true);
      if (spanName != null) {
        span.updateName(spanName);
        ServletSpanNaming.setServletUpdatedServerSpanName(context);
      }
    }

    return updateContext(context, request);
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.servlet-3.0";
  }
}
