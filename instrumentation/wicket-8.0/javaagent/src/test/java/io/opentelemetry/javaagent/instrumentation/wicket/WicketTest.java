/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.wicket;

import static org.assertj.core.api.Assertions.assertThat;

import hello.HelloApplication;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerUsingTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import java.util.EnumSet;
import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import org.apache.wicket.protocol.http.WicketFilter;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.resource.FileResource;
import org.eclipse.jetty.util.resource.Resource;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class WicketTest extends AbstractHttpServerUsingTest<Server> {

  @RegisterExtension
  public static final InstrumentationExtension testing =
      HttpServerInstrumentationExtension.forAgent();

  @Override
  protected Server setupServer() throws Exception {
    Server server = new Server(port);

    ServletContextHandler context = new ServletContextHandler(0);
    context.setContextPath(getContextPath());

    Resource resource = new FileResource(getClass().getResource("/"));
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

  @Override
  protected String getContextPath() {
    return "/jetty-context";
  }

  @BeforeAll
  void setup() {
    startServer();
  }

  @Test
  void testHello() {
    AggregatedHttpResponse response =
        client.get(address.resolve("wicket-test/").toString()).aggregate().join();
    Document doc = Jsoup.parse(response.contentUtf8());

    assertThat(response.status().code()).isEqualTo(200);
    assertThat(doc.selectFirst("#message").text()).isEqualTo("Hello World!");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET " + getContextPath() + "/wicket-test/hello.HelloPage")
                        .hasNoParent()
                        .hasKind(SpanKind.SERVER)));
  }

  @Test
  void testException() {
    AggregatedHttpResponse response =
        client.get(address.resolve("wicket-test/exception").toString()).aggregate().join();

    assertThat(response.status().code()).isEqualTo(500);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET " + getContextPath() + "/wicket-test/hello.ExceptionPage")
                        .hasKind(SpanKind.SERVER)
                        .hasNoParent()
                        .hasStatus(StatusData.error())
                        .hasException(new Exception("test exception"))));
  }
}
