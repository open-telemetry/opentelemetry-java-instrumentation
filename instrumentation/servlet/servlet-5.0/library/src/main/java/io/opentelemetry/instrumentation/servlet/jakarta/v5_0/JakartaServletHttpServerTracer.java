/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.jakarta.v5_0;

import static io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming.Source.FILTER;
import static io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming.Source.SERVLET;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming;
import io.opentelemetry.instrumentation.servlet.ServletHttpServerTracer;
import io.opentelemetry.instrumentation.servlet.naming.MappingProvider;
import io.opentelemetry.instrumentation.servlet.naming.ServletFilterMappingProvider;
import io.opentelemetry.instrumentation.servlet.naming.ServletMappingProvider;
import io.opentelemetry.instrumentation.servlet.naming.ServletSpanNameProvider;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.FilterRegistration;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletRegistration;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Collection;

public class JakartaServletHttpServerTracer
    extends ServletHttpServerTracer<ServletContext, HttpServletRequest, HttpServletResponse> {
  private static final JakartaServletHttpServerTracer TRACER = new JakartaServletHttpServerTracer();
  private static final JakartaServletSpanNameProvider SPAN_NAME_PROVIDER =
      new JakartaServletSpanNameProvider();

  public JakartaServletHttpServerTracer() {
    super(JakartaServletAccessor.INSTANCE);
  }

  public static JakartaServletHttpServerTracer tracer() {
    return TRACER;
  }

  public Context startSpan(Object servletOrFilter, HttpServletRequest request) {
    return startSpan(
        request,
        SPAN_NAME_PROVIDER.getSpanName(servletOrFilter, request),
        servletOrFilter instanceof Servlet);
  }

  public Context updateContext(
      Context context, Object servletOrFilter, HttpServletRequest request) {
    ServerSpanNaming.updateServerSpanName(
        context,
        getSpanNameSource(servletOrFilter),
        () -> SPAN_NAME_PROVIDER.getSpanNameOrNull(servletOrFilter, request));
    return updateContext(context, request);
  }

  private static ServerSpanNaming.Source getSpanNameSource(Object servletOrFilter) {
    return servletOrFilter instanceof Servlet ? SERVLET : FILTER;
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

  private static class JakartaServletSpanNameProvider
      extends ServletSpanNameProvider<ServletContext, HttpServletRequest> {
    JakartaServletSpanNameProvider() {
      super(JakartaServletAccessor.INSTANCE);
    }

    @Override
    protected MappingProvider<ServletContext> getMappingProvider(Object servletOrFilter) {
      if (servletOrFilter instanceof Servlet) {
        return getServletMappingProvider((Servlet) servletOrFilter);
      } else if (servletOrFilter instanceof Filter) {
        return getServletFilterMappingProvider((Filter) servletOrFilter);
      }

      return null;
    }

    private static MappingProvider<ServletContext> getServletMappingProvider(Servlet servlet) {
      ServletConfig servletConfig = servlet.getServletConfig();
      if (servletConfig == null) {
        return null;
      }

      String servletName = servletConfig.getServletName();
      ServletContext servletContext = servletConfig.getServletContext();
      if (servletName == null || servletContext == null) {
        return null;
      }

      return new JakartaServletMappingProvider(servletContext, servletName);
    }

    private static MappingProvider<ServletContext> getServletFilterMappingProvider(Filter filter) {
      FilterConfig filterConfig = JakartaServletFilterConfigHolder.getFilterConfig(filter);
      if (filterConfig == null) {
        return null;
      }
      String filterName = filterConfig.getFilterName();
      ServletContext servletContext = filterConfig.getServletContext();
      if (filterName == null || servletContext == null) {
        return null;
      }

      return new JakartaServletFilterMappingProvider(servletContext, filterName);
    }
  }

  private static class JakartaServletMappingProvider
      extends ServletMappingProvider<ServletContext> {
    JakartaServletMappingProvider(ServletContext servletContext, String servletName) {
      super(servletContext, servletName);
    }

    @Override
    public Collection<String> getMappings() {
      ServletRegistration servletRegistration = servletContext.getServletRegistration(servletName);
      if (servletRegistration == null) {
        return null;
      }
      return servletRegistration.getMappings();
    }
  }

  private static class JakartaServletFilterMappingProvider
      extends ServletFilterMappingProvider<ServletContext, FilterRegistration> {
    JakartaServletFilterMappingProvider(ServletContext servletContext, String servletName) {
      super(servletContext, servletName);
    }

    @Override
    public FilterRegistration getFilterRegistration() {
      return servletContext.getFilterRegistration(filterName);
    }

    @Override
    public Collection<String> getUrlPatternMappings(FilterRegistration filterRegistration) {
      return filterRegistration.getUrlPatternMappings();
    }

    @Override
    public Collection<String> getServletNameMappings(FilterRegistration filterRegistration) {
      return filterRegistration.getServletNameMappings();
    }

    @Override
    public Collection<String> getServletMappings(String servletName) {
      ServletRegistration servletRegistration = servletContext.getServletRegistration(servletName);
      if (servletRegistration == null) {
        return null;
      }
      return servletRegistration.getMappings();
    }
  }
}
