/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0.jetty.dispatch;

import static io.opentelemetry.instrumentation.servlet.v5_0.AbstractServlet5Test.HTML_PRINT_WRITER;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.AUTH_REQUIRED;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_HEADERS;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_PARAMETERS;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.QUERY_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.servlet.v5_0.RequestDispatcherServlet;
import io.opentelemetry.instrumentation.servlet.v5_0.TestServlet5;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import io.opentelemetry.javaagent.bootstrap.servlet.ExperimentalSnippetHolder;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import jakarta.servlet.Servlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.junit.jupiter.api.Test;

class JettyServlet5ForwardTest extends JettyDispatchTest {
  private static final ServerEndpoint SNIPPET_FORWARD =
      new ServerEndpoint("SNIPPET_FORWARD", "snippetForward", 200, "");
  private static final ServerEndpoint SNIPPET_FORWARD_21 =
      new ServerEndpoint("SNIPPET_FORWARD_21", "snippetForward?contentLength=21", 200, "", false);
  private static final ServerEndpoint SNIPPET_FORWARD_22 =
      new ServerEndpoint("SNIPPET_FORWARD_22", "snippetForward?contentLength=22", 200, "", false);
  private static final ServerEndpoint SNIPPET_ASYNC_RESET =
      new ServerEndpoint("SNIPPET_ASYNC_RESET", "snippetAsyncReset", 200, "");
  private static final ServerEndpoint SNIPPET_REJECTED_FORWARD =
      new ServerEndpoint("SNIPPET_REJECTED_FORWARD", "snippetRejectedForward", 200, "");
  private static final ServerEndpoint SNIPPET_WRITER_ACQUISITION_FAILURE =
      new ServerEndpoint(
          "SNIPPET_WRITER_ACQUISITION_FAILURE", "snippetWriterAcquisitionFailure", 200, "");

  @Override
  public Class<? extends Servlet> servlet() {
    return TestServlet5.Sync.class; // dispatch to sync servlet
  }

  @Override
  protected void setupServlets(ServletContextHandler context) throws Exception {
    super.setupServlets(context);

    addServlet(context, "/dispatch" + SUCCESS.getPath(), RequestDispatcherServlet.Forward.class);
    addServlet(
        context, "/dispatch" + HTML_PRINT_WRITER.getPath(), RequestDispatcherServlet.Forward.class);
    addServlet(
        context,
        "/dispatch" + HTML_SERVLET_OUTPUT_STREAM.getPath(),
        RequestDispatcherServlet.Forward.class);
    addServlet(
        context, "/dispatch" + QUERY_PARAM.getPath(), RequestDispatcherServlet.Forward.class);
    addServlet(context, "/dispatch" + REDIRECT.getPath(), RequestDispatcherServlet.Forward.class);
    addServlet(context, "/dispatch" + ERROR.getPath(), RequestDispatcherServlet.Forward.class);
    addServlet(context, "/dispatch" + EXCEPTION.getPath(), RequestDispatcherServlet.Forward.class);
    addServlet(
        context, "/dispatch" + AUTH_REQUIRED.getPath(), RequestDispatcherServlet.Forward.class);
    addServlet(
        context, "/dispatch" + CAPTURE_HEADERS.getPath(), RequestDispatcherServlet.Forward.class);
    addServlet(
        context,
        "/dispatch" + CAPTURE_PARAMETERS.getPath(),
        RequestDispatcherServlet.Forward.class);
    addServlet(
        context, "/dispatch" + INDEXED_CHILD.getPath(), RequestDispatcherServlet.Forward.class);
    addServlet(context, "/snippetForward", TestServlet5.SnippetForward.class);
    addServlet(context, "/snippetAsyncReset", TestServlet5.SnippetAsyncReset.class);
    addServlet(context, "/snippetRejectedForward", TestServlet5.SnippetRejectedForward.class);
    addServlet(
        context,
        "/snippetWriterAcquisitionFailure",
        TestServlet5.SnippetWriterAcquisitionFailure.class);
  }

  @Test
  void forwardDiscardsUncommittedSnippetBody() {
    assertReplacementBody(SNIPPET_FORWARD);
  }

  @Test
  void forwardDiscardsUncommittedSnippetBodyNearContentLengthBoundaries() {
    assertReplacementBody(SNIPPET_FORWARD_21);
    assertReplacementBody(SNIPPET_FORWARD_22);
  }

  @Test
  void asyncRawResetBufferDiscardsUncommittedSnippetBody() {
    assertReplacementBody(SNIPPET_ASYNC_RESET);
  }

  @Test
  void rejectedCommittedForwardRetainsInjectedMarker() {
    String snippet = "<!-- snippet -->";
    ExperimentalSnippetHolder.setSnippet(snippet);
    try {
      AggregatedHttpResponse response =
          client.execute(request(SNIPPET_REJECTED_FORWARD, "GET")).aggregate().join();
      String expected =
          "<head>" + snippet + "<title>before</title></head><head><title>after</title></head>";

      assertThat(response.status().code()).isEqualTo(200);
      assertThat(response.contentUtf8()).isEqualTo(expected);
      assertThat(response.contentUtf8()).containsOnlyOnce(snippet);
      if (response.headers().contentLength() >= 0) {
        assertThat(response.headers().contentLength()).isEqualTo(expected.length());
      }
    } finally {
      ExperimentalSnippetHolder.setSnippet("");
    }
  }

  @Test
  void failedWriterAcquisitionDoesNotDisableStreamInjection() {
    assertReplacementBody(SNIPPET_WRITER_ACQUISITION_FAILURE);
  }

  private void assertReplacementBody(ServerEndpoint source) {
    String snippet = "<!-- snippet -->";
    ExperimentalSnippetHolder.setSnippet(snippet);
    try {
      AggregatedHttpResponse response = client.execute(request(source, "GET")).aggregate().join();
      String expected = HTML_PRINT_WRITER.getBody().replace("<head>", "<head>" + snippet);
      assertThat(response.contentUtf8()).isEqualTo(expected);
      assertThat(response.contentUtf8()).containsOnlyOnce(snippet);
      assertThat(response.headers().contentLength()).isEqualTo(expected.length());
    } finally {
      ExperimentalSnippetHolder.setSnippet("");
    }
  }
}
