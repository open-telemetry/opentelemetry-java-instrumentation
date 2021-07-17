/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0.service;

import io.opentelemetry.instrumentation.api.servlet.MappingResolver;
import io.opentelemetry.instrumentation.servlet.naming.ServletFilterMappingResolverFactory;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.FilterRegistration;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletRegistration;
import java.util.Collection;

public class JakartaServletFilterMappingResolverFactory
    extends ServletFilterMappingResolverFactory<FilterRegistration>
    implements ContextStore.Factory<MappingResolver> {
  private final FilterConfig filterConfig;

  public JakartaServletFilterMappingResolverFactory(FilterConfig filterConfig) {
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
