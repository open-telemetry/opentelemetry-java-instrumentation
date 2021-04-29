/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.v3_0;

import static io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming.Source.FILTER;
import static io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming.Source.SERVLET;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming;
import io.opentelemetry.instrumentation.servlet.javax.JavaxServletHttpServerTracer;
import io.opentelemetry.instrumentation.servlet.naming.MappingProvider;
import io.opentelemetry.instrumentation.servlet.naming.ServletFilterMappingProvider;
import io.opentelemetry.instrumentation.servlet.naming.ServletMappingProvider;
import io.opentelemetry.instrumentation.servlet.naming.ServletSpanNameProvider;
import java.util.Collection;
import javax.servlet.Filter;
import javax.servlet.FilterConfig;
import javax.servlet.FilterRegistration;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletRegistration;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class Servlet3HttpServerTracer extends JavaxServletHttpServerTracer<HttpServletResponse> {
  private static final Servlet3HttpServerTracer TRACER = new Servlet3HttpServerTracer();
  private static final Servlet3SpanNameProvider SPAN_NAME_PROVIDER = new Servlet3SpanNameProvider();

  protected Servlet3HttpServerTracer() {
    super(Servlet3Accessor.INSTANCE);
  }

  public static Servlet3HttpServerTracer tracer() {
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
    return "io.opentelemetry.javaagent.servlet-3.0";
  }

  private static class Servlet3SpanNameProvider
      extends ServletSpanNameProvider<ServletContext, HttpServletRequest> {
    Servlet3SpanNameProvider() {
      super(Servlet3Accessor.INSTANCE);
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

      return new Servlet3MappingProvider(servletContext, servletName);
    }

    private static MappingProvider<ServletContext> getServletFilterMappingProvider(Filter filter) {
      FilterConfig filterConfig = Servlet3FilterConfigHolder.getFilterConfig(filter);
      if (filterConfig == null) {
        return null;
      }
      String filterName = filterConfig.getFilterName();
      ServletContext servletContext = filterConfig.getServletContext();
      if (filterName == null || servletContext == null) {
        return null;
      }

      return new Servlet3FilterMappingProvider(servletContext, filterName);
    }
  }

  private static class Servlet3MappingProvider extends ServletMappingProvider<ServletContext> {
    Servlet3MappingProvider(ServletContext servletContext, String servletName) {
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

  private static class Servlet3FilterMappingProvider
      extends ServletFilterMappingProvider<ServletContext, FilterRegistration> {
    Servlet3FilterMappingProvider(ServletContext servletContext, String servletName) {
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
