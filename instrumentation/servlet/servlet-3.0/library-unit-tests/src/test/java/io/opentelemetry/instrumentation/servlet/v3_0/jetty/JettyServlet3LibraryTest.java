/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.v3_0.jetty;

import io.opentelemetry.instrumentation.api.internal.HttpConstants;
import io.opentelemetry.instrumentation.servlet.v3_0.OpenTelemetryServletFilter;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import io.opentelemetry.javaagent.instrumentation.servlet.v3_0.jetty.AbstractJettyServlet3Test;
import java.util.EnumSet;
import javax.servlet.DispatcherType;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class JettyServlet3LibraryTest extends AbstractJettyServlet3Test {
  @RegisterExtension
  protected static final InstrumentationExtension testing =
      HttpServerInstrumentationExtension.forLibrary();

  @Override
  protected void addFilter(ServletContextHandler servletContext) {
    servletContext.addFilter(
        OpenTelemetryServletFilter.class, "/*", EnumSet.allOf(DispatcherType.class));
  }

  @Override
  protected void configure(HttpServerTestOptions options) {
    super.configure(options);
    options.setTestCaptureRequestParameters(false); // Requires AgentConfig.
    options.setTestCaptureHttpHeaders(false); // Requires AgentConfig.
    options.disableTestNonStandardHttpMethod(); // test doesn't use route mapping correctly.
    options.setTestException(false); // filters don't have visibility into exception handling above.
    options.setHasResponseCustomizer(e -> false);
  }

  @Override
  public String expectedHttpRoute(ServerEndpoint endpoint, String method) {
    // no need to compute route if we're not expecting it
    if (!hasHttpRouteAttribute(endpoint)) {
      return null;
    }

    if (method.equals(HttpConstants._OTHER)) {
      return getContextPath() + endpoint.getPath();
    }

    // NOTE: Primary difference from javaagent servlet instrumentation!
    // Since just we're working with a filter, we can't actually get the proper servlet path.
    return getContextPath() + "/*";
  }

  @Override
  protected void snippetInjectionWithServletOutputStream() {
    // override
  }

  @Override
  protected void snippetInjectionWithPrintWriter() {
    // override
  }
}
