/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.wicket;

import hello.HelloApplication;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterRegistration;
import java.util.EnumSet;
import org.apache.wicket.protocol.http.WicketFilter;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.resource.Resource;

class WicketTest extends AbstractWicketTest<Server> {

  @Override
  protected Server setupServer() throws Exception {
    Server server = new Server(port);

    ServletContextHandler context = new ServletContextHandler(0);
    context.setContextPath(getContextPath());

    Resource resource = Resource.newResource(getClass().getResource("/"));
    context.setBaseResource(resource);
    server.setHandler(context);

    context.addServlet(DefaultServlet.class, "/");
    FilterRegistration.Dynamic registration =
        context.getServletContext().addFilter("WicketApplication", WicketFilter.class);
    registration.setInitParameter("applicationClassName", HelloApplication.class.getName());
    registration.setInitParameter("filterMappingUrlPattern", "/wicket-test/*");
    registration.addMappingForUrlPatterns(
        EnumSet.of(DispatcherType.REQUEST), false, "/wicket-test/*");

    server.start();

    return server;
  }

  @Override
  protected void stopServer(Server server) throws Exception {
    server.stop();
    server.destroy();
  }
}
