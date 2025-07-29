/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jaxrs.v2_0;

import static org.eclipse.jetty.util.resource.Resource.newResource;

import io.opentelemetry.instrumentation.api.internal.HttpConstants;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.webapp.WebAppContext;

public abstract class JaxRsJettyHttpServerTest extends JaxRsHttpServerTest<Server> {

  @Override
  protected void configure(HttpServerTestOptions options) {
    super.configure(options);

    options.setContextPath("/rest-app");
    options.setExpectedHttpRoute(
        (endpoint, method) -> {
          if (HttpConstants._OTHER.equals(method)) {
            return getContextPath() + "/*";
          }
          return expectedHttpRoute(endpoint, method);
        });
  }

  @Override
  protected Server setupServer() throws Exception {
    WebAppContext webAppContext = new WebAppContext();
    webAppContext.setContextPath("/");
    // set up test application
    webAppContext.setBaseResource(newResource("src/test/webapp"));

    Server jettyServer = new Server(port);
    jettyServer.setHandler(webAppContext);
    jettyServer.start();

    return jettyServer;
  }

  @Override
  protected void stopServer(Server server) throws Exception {
    server.stop();
    server.destroy();
  }
}
