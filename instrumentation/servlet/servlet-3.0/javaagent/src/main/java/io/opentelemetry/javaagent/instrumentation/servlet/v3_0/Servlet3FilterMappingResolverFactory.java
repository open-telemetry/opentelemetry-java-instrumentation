/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0;

import io.opentelemetry.instrumentation.servlet.naming.ServletFilterMappingResolverFactory;
import java.util.Collection;
import javax.servlet.FilterConfig;
import javax.servlet.FilterRegistration;
import javax.servlet.ServletContext;
import javax.servlet.ServletRegistration;

public class Servlet3FilterMappingResolverFactory
    extends ServletFilterMappingResolverFactory<FilterRegistration> {
  private final FilterConfig filterConfig;

  public Servlet3FilterMappingResolverFactory(FilterConfig filterConfig) {
    this.filterConfig = filterConfig;
  }

  @Override
  protected FilterRegistration getFilterRegistration() {
    String filterName = filterConfig.getFilterName();
    ServletContext servletContext = filterConfig.getServletContext();
    if (filterName == null || servletContext == null) {
      return null;
    }
    return servletContext.getFilterRegistration(filterName);
  }

  @Override
  protected Collection<String> getUrlPatternMappings(FilterRegistration filterRegistration) {
    return filterRegistration.getUrlPatternMappings();
  }

  @Override
  protected Collection<String> getServletNameMappings(FilterRegistration filterRegistration) {
    return filterRegistration.getServletNameMappings();
  }

  @Override
  @SuppressWarnings("ReturnsNullCollection")
  protected Collection<String> getServletMappings(String servletName) {
    ServletRegistration servletRegistration =
        filterConfig.getServletContext().getServletRegistration(servletName);
    if (servletRegistration == null) {
      return null;
    }
    return servletRegistration.getMappings();
  }
}
