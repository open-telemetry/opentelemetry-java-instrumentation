/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.servlet.ServletSpanNaming;
import io.opentelemetry.instrumentation.api.tracer.ServerSpan;
import io.opentelemetry.instrumentation.servlet.ServletHttpServerTracer;
import java.util.Collection;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletRegistration;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class Servlet3HttpServerTracer extends ServletHttpServerTracer<HttpServletResponse> {

  private static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      Config.get()
          .getBooleanProperty("otel.instrumentation.servlet.experimental-span-attributes", false);

  private static final Servlet3HttpServerTracer TRACER = new Servlet3HttpServerTracer();

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

  @Override
  protected Integer peerPort(HttpServletRequest connection) {
    return connection.getRemotePort();
  }

  @Override
  protected int responseStatus(HttpServletResponse httpServletResponse) {
    return httpServletResponse.getStatus();
  }

  @Override
  protected boolean isResponseCommitted(HttpServletResponse response) {
    return response.isCommitted();
  }

  public void onTimeout(Context context, long timeout) {
    Span span = Span.fromContext(context);
    span.setStatus(StatusCode.ERROR);
    if (CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
      span.setAttribute("servlet.timeout", timeout);
    }
    span.end();
  }

  /*
  Given request already has a context associated with it.
  As there should not be nested spans of kind SERVER, we should NOT create a new span here.

  But it may happen that there is no span in current Context or it is from a different trace.
  E.g. in case of async servlet request processing we create span for incoming request in one thread,
  but actual request continues processing happens in another thread.
  Depending on servlet container implementation, this processing may again arrive into this method.
  E.g. Jetty handles async requests in a way that calls HttpServlet.service method twice.

  In this case we have to put the span from the request into current context before continuing.
  */
  public static boolean needsRescoping(Context attachedContext) {
    return !sameTrace(Span.fromContext(Context.current()), Span.fromContext(attachedContext));
  }

  private static boolean sameTrace(Span oneSpan, Span otherSpan) {
    return oneSpan.getSpanContext().getTraceId().equals(otherSpan.getSpanContext().getTraceId());
  }
}
