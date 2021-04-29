/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.naming;

public abstract class ServletMappingProvider<SERVLETCONTEXT>
    implements MappingProvider<SERVLETCONTEXT> {
  protected final SERVLETCONTEXT servletContext;
  protected final String servletName;

  public ServletMappingProvider(SERVLETCONTEXT servletContext, String servletName) {
    this.servletContext = servletContext;
    this.servletName = servletName;
  }

  @Override
  public SERVLETCONTEXT getServletContext() {
    return servletContext;
  }

  @Override
  public String getMappingKey() {
    return "servlet." + servletName;
  }
}
