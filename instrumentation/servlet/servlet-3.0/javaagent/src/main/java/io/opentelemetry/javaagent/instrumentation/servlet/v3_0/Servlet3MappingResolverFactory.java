/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0;

import io.opentelemetry.instrumentation.servlet.naming.ServletMappingResolverFactory;
import javax.annotation.Nullable;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletRegistration;

public class Servlet3MappingResolverFactory extends ServletMappingResolverFactory {
  private final ServletConfig servletConfig;

  public Servlet3MappingResolverFactory(ServletConfig servletConfig) {
    this.servletConfig = servletConfig;
  }

  @Override
  @Nullable
  public Mappings getMappings() {
    String servletName = servletConfig.getServletName();
    ServletContext servletContext = servletConfig.getServletContext();
    if (servletName == null || servletContext == null) {
      return null;
    }

    ServletRegistration servletRegistration = servletContext.getServletRegistration(servletName);
    if (servletRegistration == null) {
      return null;
    }
    return new Mappings(servletRegistration.getMappings());
  }
}
