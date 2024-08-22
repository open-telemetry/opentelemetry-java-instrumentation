/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tapestry;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerUsingTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.testing.internal.armeria.client.WebClient;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import java.net.InetSocketAddress;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.webapp.WebAppContext;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class TapestryTest extends AbstractHttpServerUsingTest<Server> {

  private static WebClient client;

  @RegisterExtension
  public static final InstrumentationExtension testing =
      HttpServerInstrumentationExtension.forAgent();

  @Override
  protected Server setupServer() throws Exception {
    WebAppContext webAppContext = new WebAppContext();
    webAppContext.setContextPath(getContextPath());
    Server jettyServer = new Server(new InetSocketAddress("localhost", port));

    // set up test application
    webAppContext.setBaseResource(Resource.newResource("src/test/webapp"));
    jettyServer.setHandler(webAppContext);
    jettyServer.start();

    return jettyServer;
  }

  @Override
  protected void stopServer(Server server) throws Exception {
    server.stop();
  }

  @Override
  protected String getContextPath() {
    return "/jetty-context";
  }

  @BeforeAll
  void setup() {
    startServer();
    client = WebClient.builder(address).followRedirects().build();
  }

  @Test
  void testIndexPage() {
    AggregatedHttpResponse response = client.get("/").aggregate().join();
    Document doc = Jsoup.parse(response.contentUtf8());

    assertThat(response.status().code()).isEqualTo(200);
    assertThat(doc.selectFirst("title").text()).isEqualTo("Index page");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET " + getContextPath() + "/Index")
                        .hasNoParent()
                        .hasKind(SpanKind.SERVER),
                span ->
                    span.hasName("activate/Index")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))));
  }

  @Test
  void testStartAction() {
    AggregatedHttpResponse response = client.get("/index.start").aggregate().join();
    Document doc = Jsoup.parse(response.contentUtf8());

    assertThat(response.status().code()).isEqualTo(200);
    assertThat(doc.selectFirst("title").text()).isEqualTo("Other page");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET " + getContextPath() + "/Index")
                        .hasNoParent()
                        .hasKind(SpanKind.SERVER),
                span ->
                    span.hasName("activate/Index")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0)),
                span ->
                    span.hasName("action/Index:start")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0)),
                span ->
                    span.hasName("Response.sendRedirect")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(2))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET " + getContextPath() + "/Other")
                        .hasNoParent()
                        .hasKind(SpanKind.SERVER),
                span ->
                    span.hasName("activate/Other")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))));
  }

  @Test
  void testExceptionAction() {
    AggregatedHttpResponse response = client.get("/index.exception").aggregate().join();

    assertThat(response.status().code()).isEqualTo(500);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET " + getContextPath() + "/Index")
                        .hasStatus(StatusData.error())
                        .hasKind(SpanKind.SERVER),
                span ->
                    span.hasName("activate/Index")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0)),
                span ->
                    span.hasName("action/Index:exception")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasStatus(StatusData.error())
                        .hasException(new IllegalStateException("expected"))));
  }
}
