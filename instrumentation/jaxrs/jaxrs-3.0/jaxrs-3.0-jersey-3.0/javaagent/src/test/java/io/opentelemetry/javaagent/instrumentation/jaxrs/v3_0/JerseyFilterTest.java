/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v3_0;

import io.opentelemetry.instrumentation.jaxrs.v3_0.JaxRsFilterTest;
import io.opentelemetry.instrumentation.jaxrs.v3_0.test.Resource;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import jakarta.servlet.Servlet;
import jakarta.ws.rs.core.Application;
import java.util.HashSet;
import java.util.Set;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;

class JerseyFilterTest extends JaxRsFilterTest<Server> {
  @RegisterExtension
  static final InstrumentationExtension testing = HttpServerInstrumentationExtension.forAgent();

  @BeforeAll
  void setUp() {
    startServer();
  }

  @AfterAll
  void cleanUp() {
    cleanupServer();
  }

  @Override
  protected Server setupServer() throws Exception {
    Servlet servlet = new ServletContainer(ResourceConfig.forApplication(new TestApplication()));

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
  protected String getContextPath() {
    return "/*";
  }

  @Override
  protected boolean runsOnServer() {
    return true;
  }

  @Override
  protected String defaultServerRoute() {
    return "/*";
  }

  @Override
  protected TestResponse makeRequest(String url) {
    AggregatedHttpResponse response =
        client.post(address.resolve(url).toString(), "").aggregate().join();
    return new TestResponse(response.contentUtf8(), response.status().code());
  }

  class TestApplication extends Application {
    @Override
    public Set<Class<?>> getClasses() {
      Set<Class<?>> classes = new HashSet<>();
      classes.add(Resource.Test1.class);
      classes.add(Resource.Test2.class);
      classes.add(Resource.Test3.class);
      return classes;
    }

    @Override
    public Set<Object> getSingletons() {
      Set<Object> singletons = new HashSet<>();
      singletons.add(simpleRequestFilter);
      singletons.add(prematchRequestFilter);
      return singletons;
    }
  }
}
