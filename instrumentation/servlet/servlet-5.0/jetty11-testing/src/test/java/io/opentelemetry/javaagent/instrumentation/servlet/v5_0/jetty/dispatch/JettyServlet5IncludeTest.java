/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0.jetty.dispatch;

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
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import io.opentelemetry.javaagent.bootstrap.servlet.ExperimentalSnippetHolder;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import jakarta.servlet.Servlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.junit.jupiter.api.Test;

class JettyServlet5IncludeTest extends JettyDispatchTest {
  private static final ServerEndpoint SNIPPET_INCLUDE_RAW_RESET =
      new ServerEndpoint("SNIPPET_INCLUDE_RAW_RESET", "snippetAsyncIncludeRawReset", 200, "");
  private static final ServerEndpoint SNIPPET_INCLUDE_FIXED_LENGTH_RESET =
      new ServerEndpoint(
          "SNIPPET_INCLUDE_FIXED_LENGTH_RESET", "snippetAsyncIncludeFixedLengthReset", 200, "");
  private static final ServerEndpoint SNIPPET_INCLUDE_SPLIT_HEAD_FIXED_LENGTH =
      new ServerEndpoint(
          "SNIPPET_INCLUDE_SPLIT_HEAD_FIXED_LENGTH",
          "snippetAsyncIncludeSplitHeadFixedLength/setContentLength",
          200,
          "");
  private static final ServerEndpoint SNIPPET_CONTENT_LENGTH_REMOVAL_HEADER =
      new ServerEndpoint(
          "SNIPPET_CONTENT_LENGTH_REMOVAL_HEADER",
          "snippetContentLengthRemoval/setHeader",
          200,
          "");
  private static final ServerEndpoint SNIPPET_CONTENT_LENGTH_REMOVAL_NUMERIC =
      new ServerEndpoint(
          "SNIPPET_CONTENT_LENGTH_REMOVAL_NUMERIC",
          "snippetContentLengthRemoval/setContentLength",
          200,
          "");

  @Override
  public Class<? extends Servlet> servlet() {
    return TestServlet5.Sync.class; // dispatch to sync servlet
  }

  @Override
  protected void configure(HttpServerTestOptions options) {
    super.configure(options);
    options.setTestRedirect(false);
    options.setTestCaptureHttpHeaders(false);
    options.setTestError(false);
  }

  @Override
  protected void setupServlets(ServletContextHandler context) throws Exception {
    super.setupServlets(context);

    addServlet(context, "/dispatch" + SUCCESS.getPath(), RequestDispatcherServlet.Include.class);
    addServlet(
        context, "/dispatch" + HTML_PRINT_WRITER.getPath(), RequestDispatcherServlet.Include.class);
    addServlet(
        context,
        "/dispatch" + HTML_SERVLET_OUTPUT_STREAM.getPath(),
        RequestDispatcherServlet.Include.class);
    addServlet(
        context, "/dispatch" + QUERY_PARAM.getPath(), RequestDispatcherServlet.Include.class);
    addServlet(context, "/dispatch" + REDIRECT.getPath(), RequestDispatcherServlet.Include.class);
    addServlet(context, "/dispatch" + ERROR.getPath(), RequestDispatcherServlet.Include.class);
    addServlet(context, "/dispatch" + EXCEPTION.getPath(), RequestDispatcherServlet.Include.class);
    addServlet(
        context, "/dispatch" + AUTH_REQUIRED.getPath(), RequestDispatcherServlet.Include.class);
    addServlet(
        context, "/dispatch" + CAPTURE_HEADERS.getPath(), RequestDispatcherServlet.Include.class);
    addServlet(
        context,
        "/dispatch" + CAPTURE_PARAMETERS.getPath(),
        RequestDispatcherServlet.Include.class);
    addServlet(
        context, "/dispatch" + INDEXED_CHILD.getPath(), RequestDispatcherServlet.Include.class);
    addServlet(
        context, "/snippetAsyncIncludeRawReset", TestServlet5.SnippetAsyncIncludeRawReset.class);
    addServlet(
        context, "/snippetIncludeRawResetTarget", TestServlet5.SnippetIncludeRawResetTarget.class);
    addServlet(
        context,
        "/snippetAsyncIncludeFixedLengthReset",
        TestServlet5.SnippetAsyncIncludeFixedLengthReset.class);
    addServlet(
        context,
        "/snippetIncludeFixedLengthResetTarget",
        TestServlet5.SnippetIncludeFixedLengthResetTarget.class);
    addServlet(
        context,
        "/snippetAsyncIncludeSplitHeadFixedLength/setContentLength",
        TestServlet5.SnippetAsyncIncludeSplitHeadFixedLength.class);
    addServlet(
        context,
        "/snippetIncludeSplitHeadTarget",
        TestServlet5.SnippetIncludeSplitHeadTarget.class);
    addServlet(
        context,
        "/snippetContentLengthRemoval/setHeader",
        TestServlet5.SnippetContentLengthRemoval.class);
    addServlet(
        context,
        "/snippetContentLengthRemoval/setContentLength",
        TestServlet5.SnippetContentLengthRemoval.class);
  }

  @Test
  void rawResponseResetDuringIncludeDiscardsBodyAndAllowsInjection() {
    String snippet = "<!-- snippet -->";
    ExperimentalSnippetHolder.setSnippet(snippet);
    try {
      AggregatedHttpResponse response =
          client.execute(request(SNIPPET_INCLUDE_RAW_RESET, "GET")).aggregate().join();
      String expected = HTML_PRINT_WRITER.getBody().replace("<head>", "<head>" + snippet);

      assertThat(response.contentUtf8()).isEqualTo(expected);
      assertThat(response.contentUtf8()).containsOnlyOnce(snippet);
      assertThat(response.contentUtf8()).doesNotContain("discarded-prefix");
      if (response.headers().contentLength() >= 0) {
        assertThat(response.headers().contentLength()).isEqualTo(response.content().length());
      }
    } finally {
      ExperimentalSnippetHolder.setSnippet("");
    }
  }

  @Test
  void fixedLengthRawResetDuringIncludeRestoresDeclaredLength() {
    String snippet = "<!-- snippet -->";
    ExperimentalSnippetHolder.setSnippet(snippet);
    try {
      AggregatedHttpResponse response =
          client.execute(request(SNIPPET_INCLUDE_FIXED_LENGTH_RESET, "GET")).aggregate().join();

      assertThat(response.contentUtf8()).isEqualTo(TestServlet5.SNIPPET_RESET_REPLACEMENT_BODY);
      assertThat(response.contentUtf8()).doesNotContain(snippet);
      assertThat(response.headers().contentLength()).isEqualTo(response.content().length());
    } finally {
      ExperimentalSnippetHolder.setSnippet("");
    }
  }

  @Test
  void fixedLengthSplitHeadIncludeUsesJettyPrefixedHeaders() {
    String snippet = "<!-- snippet -->";
    ExperimentalSnippetHolder.setSnippet(snippet);
    try {
      AggregatedHttpResponse response =
          client
              .execute(request(SNIPPET_INCLUDE_SPLIT_HEAD_FIXED_LENGTH, "GET"))
              .aggregate()
              .join();
      String expected = "<head>" + snippet + "<title>included</title></head>";

      assertThat(response.contentUtf8()).isEqualTo(expected);
      assertThat(response.contentUtf8()).containsOnlyOnce(snippet);
      assertThat(response.headers().get("X-Included")).isEqualTo("accepted");
      assertThat(response.headers().contentLength()).isEqualTo(response.content().length());
    } finally {
      ExperimentalSnippetHolder.setSnippet("");
    }
  }

  @Test
  void acceptedContentLengthRemovalDoesNotRestoreStaleLength() {
    for (ServerEndpoint endpoint :
        new ServerEndpoint[] {
          SNIPPET_CONTENT_LENGTH_REMOVAL_HEADER, SNIPPET_CONTENT_LENGTH_REMOVAL_NUMERIC
        }) {
      String snippet = "<!-- snippet -->";
      ExperimentalSnippetHolder.setSnippet(snippet);
      try {
        AggregatedHttpResponse response =
            client.execute(request(endpoint, "GET")).aggregate().join();

        assertThat(response.contentUtf8()).isEqualTo(TestServlet5.SNIPPET_RESET_REPLACEMENT_BODY);
        assertThat(response.contentUtf8()).doesNotContain(snippet);
        if (response.headers().contentLength() >= 0) {
          assertThat(response.headers().contentLength()).isEqualTo(response.content().length());
        }
      } finally {
        ExperimentalSnippetHolder.setSnippet("");
      }
    }
  }
}
