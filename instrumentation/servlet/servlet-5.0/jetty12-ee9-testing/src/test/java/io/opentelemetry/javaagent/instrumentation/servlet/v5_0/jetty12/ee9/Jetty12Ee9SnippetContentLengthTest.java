/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0.jetty12.ee9;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.servlet.v5_0.AbstractServlet5Test;
import io.opentelemetry.instrumentation.servlet.v5_0.TestServlet5;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import io.opentelemetry.javaagent.bootstrap.servlet.ExperimentalSnippetHolder;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import io.opentelemetry.testing.internal.armeria.common.SplitHttpResponse;
import jakarta.servlet.Servlet;
import java.net.InetSocketAddress;
import org.eclipse.jetty.ee9.servlet.ServletContextHandler;
import org.eclipse.jetty.server.Server;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class Jetty12Ee9SnippetContentLengthTest
    extends AbstractServlet5Test<Server, ServletContextHandler> {

  private static final ServerEndpoint SNIPPET_ASYNC_LENGTH =
      new ServerEndpoint("SNIPPET_ASYNC_LENGTH", "snippetAsyncContentLength", 200, "");
  private static final ServerEndpoint SNIPPET_ASYNC_LENGTH_CLEAR =
      new ServerEndpoint(
          "SNIPPET_ASYNC_LENGTH_CLEAR", "snippetAsyncContentLength?length=clear", 200, "", false);
  private static final ServerEndpoint SNIPPET_ASYNC_SAME_LENGTH_RESET =
      new ServerEndpoint("SNIPPET_ASYNC_SAME_LENGTH_RESET", "snippetAsyncSameLengthReset", 200, "");
  private static final ServerEndpoint SNIPPET_INCLUDE_SPLIT_HEAD_FIXED_LENGTH =
      new ServerEndpoint(
          "SNIPPET_INCLUDE_SPLIT_HEAD_FIXED_LENGTH",
          "snippetAsyncIncludeSplitHeadFixedLength",
          200,
          "");

  @RegisterExtension
  protected static final InstrumentationExtension testing =
      HttpServerInstrumentationExtension.forAgent();

  @Override
  protected void configure(HttpServerTestOptions options) {
    super.configure(options);
    options.setTestNotFound(false);
    options.setTestError(false);
    options.setContextPath("/jetty-context");
  }

  @Override
  public Class<? extends Servlet> servlet() {
    return TestServlet5.Async.class;
  }

  @Override
  protected Server setupServer() throws Exception {
    Server server = new Server(new InetSocketAddress("localhost", port));
    ServletContextHandler context = new ServletContextHandler();
    context.setContextPath(getContextPath());
    setupServlets(context);
    server.setHandler(context);
    server.start();
    return server;
  }

  @Override
  public void stopServer(Server server) throws Exception {
    server.stop();
    server.destroy();
  }

  @Override
  public void addServlet(
      ServletContextHandler context, String path, Class<? extends Servlet> servlet) {
    context.addServlet(servlet, path);
  }

  @Override
  protected void setupServlets(ServletContextHandler context) throws Exception {
    super.setupServlets(context);
    addServlet(context, "/snippetAsyncContentLength", TestServlet5.SnippetAsyncContentLength.class);
    addServlet(
        context,
        "/snippetAsyncContentLengthTarget",
        TestServlet5.SnippetAsyncContentLengthTarget.class);
    addServlet(
        context,
        "/snippetAsyncIncludeSplitHeadFixedLength",
        TestServlet5.SnippetAsyncIncludeSplitHeadFixedLength.class);
    addServlet(context, "/snippetSameLengthReset", TestServlet5.SnippetSameLengthReset.class);
    addServlet(
        context, "/snippetAsyncSameLengthReset", TestServlet5.SnippetAsyncSameLengthReset.class);
    addServlet(
        context,
        "/snippetIncludeSplitHeadTarget",
        TestServlet5.SnippetIncludeSplitHeadTarget.class);
  }

  @Test
  void asyncRawContentLengthChanges() {
    assertAsyncContentLength(SNIPPET_ASYNC_LENGTH, false);
    assertAsyncContentLength(SNIPPET_ASYNC_LENGTH_CLEAR, true);
  }

  @Test
  void fixedLengthSplitHeadIncludeAdjustsWireLength() {
    String snippet = "<!-- snippet -->";
    ExperimentalSnippetHolder.setSnippet(snippet);
    try {
      String expected = "<head>" + snippet + "<title>included</title></head>";
      int expectedLength = expected.getBytes(UTF_8).length;
      AggregatedHttpResponse response =
          client
              .execute(request(SNIPPET_INCLUDE_SPLIT_HEAD_FIXED_LENGTH, "GET"))
              .peekHeaders(headers ->
                  assertThat(headers.get("content-length"))
                      .isEqualTo(Integer.toString(expectedLength)))
              .aggregate()
              .join();

      assertThat(response.status().code()).isEqualTo(200);
      assertThat(response.contentUtf8()).isEqualTo(expected);
      assertThat(response.contentUtf8()).containsOnlyOnce(snippet);
    } finally {
      ExperimentalSnippetHolder.setSnippet("");
    }
  }

  @Test
  void wrappedSameLengthContentLengthDeclarationPreservesReplacementLength() {
    String snippet = "<!-- snippet -->";
    String replacementBody = TestServlet5.SNIPPET_RESET_REPLACEMENT_BODY;
    ExperimentalSnippetHolder.setSnippet(snippet);
    try {
      ServerEndpoint endpoint =
          new ServerEndpoint(
              "SNIPPET_SAME_LENGTH_RESET",
              "snippetSameLengthReset?contentLength="
                  + (replacementBody.length() - snippet.length()),
              200,
              "");
      SplitHttpResponse splitResponse = client.execute(request(endpoint, "GET")).split();
      assertThat(splitResponse.headers().join().contentLength())
          .isEqualTo(replacementBody.length());
      assertThat(splitResponse.unsplit().aggregate().join().contentUtf8())
          .isEqualTo(replacementBody);
    } finally {
      ExperimentalSnippetHolder.setSnippet("");
    }
  }

  @Test
  void asyncRawSameLengthContentLengthDeclarationPreservesReplacementLength() {
    String snippet = "<!-- snippet -->";
    ExperimentalSnippetHolder.setSnippet(snippet);
    try {
      SplitHttpResponse splitResponse =
          client.execute(request(SNIPPET_ASYNC_SAME_LENGTH_RESET, "GET")).split();
      int expectedLength = TestServlet5.SNIPPET_RESET_REPLACEMENT_BODY.length() + snippet.length();
      assertThat(splitResponse.headers().join().contentLength()).isEqualTo(expectedLength);
      assertThat(splitResponse.unsplit().aggregate().join().contentUtf8())
          .isEqualTo(replacementBody(expectedLength));
    } finally {
      ExperimentalSnippetHolder.setSnippet("");
    }
  }

  private void assertAsyncContentLength(ServerEndpoint endpoint, boolean cleared) {
    String snippet = "<!-- snippet -->";
    ExperimentalSnippetHolder.setSnippet(snippet);
    try {
      String expected = "<head>" + snippet + "async response</head>";
      String expectedLength = Integer.toString(expected.getBytes(UTF_8).length);
      AggregatedHttpResponse response =
          client
              .execute(request(endpoint, "GET"))
              .peekHeaders(headers -> {
                String contentLength = headers.get("content-length");
                if (cleared) {
                  assertThat(contentLength).isIn((String) null, expectedLength);
                } else {
                  assertThat(contentLength).isEqualTo(expectedLength);
                }
              })
              .aggregate()
              .join();

      assertThat(response.status().code()).isEqualTo(200);
      assertThat(response.contentUtf8()).isEqualTo(expected);
      assertThat(response.contentUtf8()).containsOnlyOnce(snippet);
    } finally {
      ExperimentalSnippetHolder.setSnippet("");
    }
  }

  private static String replacementBody(int length) {
    StringBuilder body = new StringBuilder(TestServlet5.SNIPPET_RESET_REPLACEMENT_BODY);
    while (body.length() < length) {
      body.append('x');
    }
    return body.toString();
  }
}
