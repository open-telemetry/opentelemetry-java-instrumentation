/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v3_0;

import io.opentelemetry.instrumentation.api.internal.HttpConstants;
import io.opentelemetry.instrumentation.jaxrs.v3_0.JaxRsHttpServerTest;
import io.opentelemetry.instrumentation.jaxrs.v3_0.test.JaxRsTestApplication;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import jakarta.servlet.Servlet;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.junit.jupiter.api.extension.RegisterExtension;

class JerseyHttpServerTest extends JaxRsHttpServerTest<Server> {
  @RegisterExtension
  static final InstrumentationExtension testing = HttpServerInstrumentationExtension.forAgent();

  @Override
  protected Server setupServer() throws Exception {
    Servlet servlet =
        new ServletContainer(ResourceConfig.forApplicationClass(JaxRsTestApplication.class));

    ServletContextHandler handler = new ServletContextHandler(ServletContextHandler.SESSIONS);
    handler.setContextPath("/");
    handler.addServlet(new ServletHolder(servlet), "/*");

    Server server = new Server(port);
    server.setHandler(handler);
    server.start();

    return server;
  }

  @Override
  protected void stopServer(Server server) throws Exception {
    server.stop();
  }

  @Override
  protected boolean asyncCancelHasSendError() {
    return true;
  }

  @Override
  protected boolean testInterfaceMethodWithPath() {
    // disables a test that jersey deems invalid
    return false;
  }

  @Override
  protected void configure(HttpServerTestOptions options) {
    super.configure(options);

    options.setResponseCodeOnNonStandardHttpMethod(500);
    options.setExpectedHttpRoute(
        (endpoint, method) -> {
          if (HttpConstants._OTHER.equals(method)) {
            return getContextPath() + "/*";
          }
          return expectedHttpRoute(endpoint, method);
        });
  }
}
