/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0.jetty12.dispatch;

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
import io.opentelemetry.testing.internal.armeria.common.SplitHttpResponse;
import jakarta.servlet.Servlet;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.junit.jupiter.api.Test;

class Jetty12Servlet5ForwardTest extends Jetty12DispatchTest {
  private static final ServerEndpoint SNIPPET_ASYNC_SAME_LENGTH_RESET =
      new ServerEndpoint("SNIPPET_ASYNC_SAME_LENGTH_RESET", "snippetAsyncSameLengthReset", 200, "");

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
    addServlet(context, "/snippetSameLengthReset", TestServlet5.SnippetSameLengthReset.class);
    addServlet(
        context, "/snippetAsyncSameLengthReset", TestServlet5.SnippetAsyncSameLengthReset.class);
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

  private static String replacementBody(int length) {
    StringBuilder body = new StringBuilder(TestServlet5.SNIPPET_RESET_REPLACEMENT_BODY);
    while (body.length() < length) {
      body.append('x');
    }
    return body.toString();
  }
}
