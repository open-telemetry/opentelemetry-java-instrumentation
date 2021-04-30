/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0;

import io.opentelemetry.instrumentation.servlet.naming.MappingResolver;
import io.opentelemetry.instrumentation.servlet.naming.ServletMappingResolverFactory;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import java.util.Collection;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletRegistration;

public class Servlet3MappingResolverFactory extends ServletMappingResolverFactory
    implements ContextStore.Factory<MappingResolver> {
  private final ServletConfig servletConfig;

  public Servlet3MappingResolverFactory(ServletConfig servletConfig) {
    System.err.println("aaaaaaaa " + servletConfig);
    new Exception().printStackTrace();
    this.servletConfig = servletConfig;
  }

  public Collection<String> getMappings() {
    System.err.println("--------- " + servletConfig);
    if (servletConfig == null) {
      return null;
    }

    String servletName = servletConfig.getServletName();
    ServletContext servletContext = servletConfig.getServletContext();
    System.err.println("--------- " + servletName + " " + servletContext);
    if (servletName == null || servletContext == null) {
      return null;
    }

    ServletRegistration servletRegistration = servletContext.getServletRegistration(servletName);
    if (servletRegistration == null) {
      return null;
    }
    return servletRegistration.getMappings();
  }
}
