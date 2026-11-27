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
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.servlet.v5_0.TestServlet5;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import io.opentelemetry.javaagent.bootstrap.servlet.ExperimentalSnippetHolder;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import jakarta.servlet.Servlet;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.junit.jupiter.api.Test;

class Jetty12Servlet5DispatchAsyncTest extends Jetty12DispatchTest {
  private static final ServerEndpoint SNIPPET_ASYNC_LENGTH =
      new ServerEndpoint("SNIPPET_ASYNC_LENGTH", "snippetAsyncContentLength", 200, "");
  private static final ServerEndpoint SNIPPET_ASYNC_LENGTH_CLEAR =
      new ServerEndpoint(
          "SNIPPET_ASYNC_LENGTH_CLEAR", "snippetAsyncContentLength?length=clear", 200, "", false);

  @Override
  public Class<? extends Servlet> servlet() {
    return TestServlet5.Async.class;
  }

  @Override
  protected void setupServlets(ServletContextHandler context) throws Exception {
    super.setupServlets(context);
    addServlet(
        context, "/dispatch" + HTML_PRINT_WRITER.getPath(), TestServlet5.DispatchAsync.class);
    addServlet(
        context,
        "/dispatch" + HTML_SERVLET_OUTPUT_STREAM.getPath(),
        TestServlet5.DispatchAsync.class);
    addServlet(context, "/dispatch" + SUCCESS.getPath(), TestServlet5.DispatchAsync.class);
    addServlet(context, "/dispatch" + QUERY_PARAM.getPath(), TestServlet5.DispatchAsync.class);
    addServlet(context, "/dispatch" + ERROR.getPath(), TestServlet5.DispatchAsync.class);
    addServlet(context, "/dispatch" + EXCEPTION.getPath(), TestServlet5.DispatchAsync.class);
    addServlet(context, "/dispatch" + REDIRECT.getPath(), TestServlet5.DispatchAsync.class);
    addServlet(context, "/dispatch" + AUTH_REQUIRED.getPath(), TestServlet5.DispatchAsync.class);
    addServlet(context, "/dispatch" + CAPTURE_HEADERS.getPath(), TestServlet5.DispatchAsync.class);
    addServlet(
        context, "/dispatch" + CAPTURE_PARAMETERS.getPath(), TestServlet5.DispatchAsync.class);
    addServlet(context, "/dispatch" + INDEXED_CHILD.getPath(), TestServlet5.DispatchAsync.class);
    addServlet(context, "/dispatch/recursive", TestServlet5.DispatchRecursive.class);
    addServlet(context, "/snippetAsyncContentLength", TestServlet5.SnippetAsyncContentLength.class);
    addServlet(
        context,
        "/snippetAsyncContentLengthTarget",
        TestServlet5.SnippetAsyncContentLengthTarget.class);
  }

  @Test
  void asyncRawContentLengthChanges() {
    assertAsyncContentLength(SNIPPET_ASYNC_LENGTH, true);
    assertAsyncContentLength(SNIPPET_ASYNC_LENGTH_CLEAR, false);
  }

  private void assertAsyncContentLength(ServerEndpoint endpoint, boolean fixedLength) {
    String snippet = "<!-- snippet -->";
    ExperimentalSnippetHolder.setSnippet(snippet);
    try {
      String expected = "<head>" + snippet + "async response</head>";
      int expectedLength = expected.getBytes(UTF_8).length;
      AggregatedHttpResponse response =
          client
              .execute(request(endpoint, "GET"))
              .peekHeaders(headers -> {
                String contentLength = headers.get("content-length");
                if (fixedLength || contentLength != null) {
                  assertThat(contentLength).isEqualTo(Integer.toString(expectedLength));
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

  @Override
  public boolean errorEndpointUsesSendError() {
    return false;
  }
}
