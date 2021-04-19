/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.jakarta.v5_0;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.api.servlet.ServletSpanNaming;
import io.opentelemetry.instrumentation.api.tracer.ServerSpan;
import io.opentelemetry.instrumentation.servlet.MappingResolver;
import io.opentelemetry.instrumentation.servlet.ServletHttpServerTracer;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletRegistration;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Collection;

public class JakartaServletHttpServerTracer
    extends ServletHttpServerTracer<HttpServletRequest, HttpServletResponse> {
  private static final JakartaServletHttpServerTracer TRACER = new JakartaServletHttpServerTracer();

  public JakartaServletHttpServerTracer() {
    super(JakartaServletAccessor.INSTANCE);
  }

  public static JakartaServletHttpServerTracer tracer() {
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
    updateServerSpanName(context, servletOrFilter, request);
    return updateContext(context, request);
  }

  private void updateServerSpanName(
      Context context, Object servletOrFilter, HttpServletRequest request) {
    Span span = ServerSpan.fromContextOrNull(context);
    if (span != null) {
      ServletSpanNaming servletSpanNaming = ServletSpanNaming.from(context);
      if (servletSpanNaming.shouldServletUpdateServerSpanName()) {
        String spanName = getSpanNameFromPath(servletOrFilter, request);
        if (spanName != null) {
          span.updateName(spanName);
          servletSpanNaming.setServletUpdatedServerSpanName();
        }
      }
    }
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.servlet-5.0";
  }

  @Override
  protected TextMapGetter<HttpServletRequest> getGetter() {
    return JakartaHttpServletRequestGetter.GETTER;
  }

  @Override
  protected String errorExceptionAttributeName() {
    return RequestDispatcher.ERROR_EXCEPTION;
  }
}
