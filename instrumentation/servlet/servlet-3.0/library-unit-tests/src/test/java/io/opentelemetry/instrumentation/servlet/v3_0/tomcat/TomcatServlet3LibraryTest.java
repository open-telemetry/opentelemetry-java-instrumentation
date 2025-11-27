/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.v3_0.tomcat;

import io.opentelemetry.instrumentation.api.internal.HttpConstants;
import io.opentelemetry.instrumentation.servlet.v3_0.OpenTelemetryServletFilter;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import io.opentelemetry.javaagent.instrumentation.servlet.v3_0.tomcat.AbstractTomcatServlet3Test;
import org.apache.catalina.Container;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardEngine;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class TomcatServlet3LibraryTest extends AbstractTomcatServlet3Test {

  @RegisterExtension
  protected static final InstrumentationExtension testing =
      HttpServerInstrumentationExtension.forLibrary();

  @SuppressWarnings("deprecation") // needed API also on Engine.
  @Override
  protected void beforeStart(Tomcat tomcatServer) {
    StandardEngine engine =
        (StandardEngine) tomcatServer.getServer().findService("Tomcat").getContainer();
    Container container = engine.findChild(engine.getDefaultHost());
    StandardContext context = (StandardContext) container.findChild(getContextPath());

    FilterDef filter1definition = new FilterDef();
    filter1definition.setFilterName(OpenTelemetryServletFilter.class.getSimpleName());
    filter1definition.setFilterClass(OpenTelemetryServletFilter.class.getName());
    context.addFilterDef(filter1definition);

    FilterMap filter1mapping = new FilterMap();
    filter1mapping.setFilterName(OpenTelemetryServletFilter.class.getSimpleName());
    filter1mapping.addURLPattern("/*");
    context.addFilterMap(filter1mapping);
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
