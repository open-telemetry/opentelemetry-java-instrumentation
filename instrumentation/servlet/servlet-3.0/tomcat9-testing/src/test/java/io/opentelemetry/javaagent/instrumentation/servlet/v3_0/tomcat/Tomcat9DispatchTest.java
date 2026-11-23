/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0.tomcat;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.opentelemetry.instrumentation.servlet.v3_0.tomcat.BaseTomcatDispatchTest;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import io.opentelemetry.javaagent.bootstrap.servlet.ExperimentalSnippetHolder;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.catalina.Context;
import org.apache.tomcat.util.descriptor.web.ErrorPage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class Tomcat9DispatchTest extends BaseTomcatDispatchTest {
  private static final ServerEndpoint SNIPPET_ERROR_PAGE =
      new ServerEndpoint("SNIPPET_ERROR_PAGE", "snippetErrorPageInitial", 500, "");
  private static final String SAME_LENGTH_RESET_BODY = "replacement response without a head";
  private static final String SAME_LENGTH_RESET_SNIPPET = "<!-- snippet -->";
  private static final String ABANDONED_BODY = "<head>abandoned response</head>";
  private static final String ERROR_BODY = "<head>replacement error page body</head>";

  @RegisterExtension
  private static final InstrumentationExtension testing =
      HttpServerInstrumentationExtension.forAgent();

  @Override
  protected void setupServlets(Context context) throws Exception {
    super.setupServlets(context);
    addServlet(context, SNIPPET_ERROR_PAGE.getPath(), SnippetErrorPageInitial.class);
    addServlet(context, "/snippetErrorPage", SnippetErrorPage.class);
    addServlet(context, "/snippetSameLengthReset", SnippetSameLengthReset.class);

    ErrorPage errorPage = new ErrorPage();
    errorPage.setErrorCode(500);
    errorPage.setLocation("/snippetErrorPage");
    context.addErrorPage(errorPage);
  }

  @BeforeAll
  static void bothServletNamespacesAreAvailable() throws ClassNotFoundException {
    Class.forName("javax.servlet.ServletResponse");
    Class.forName("jakarta.servlet.ServletResponse");
  }

  @Test
  void registeredErrorPageDiscardsAdjustedContentLength() {
    assumeTrue(isAgentTest());

    String snippet = "<!-- snippet -->";
    String expectedBody = ERROR_BODY.replace("<head>", "<head>" + snippet);
    ExperimentalSnippetHolder.setSnippet(snippet);
    try {
      AtomicReference<String> contentLength = new AtomicReference<>();
      AggregatedHttpResponse response =
          client
              .execute(request(SNIPPET_ERROR_PAGE, "GET"))
              .peekHeaders(headers -> contentLength.set(headers.get("content-length")))
              .aggregate()
              .join();

      assertThat(response.status().code()).isEqualTo(500);
      assertThat(response.contentUtf8()).isEqualTo(expectedBody);
      assertThat(response.contentUtf8()).containsOnlyOnce(snippet);
      assertThat(response.contentUtf8()).doesNotContain("abandoned");
      if (contentLength.get() != null) {
        assertThat(contentLength.get())
            .isEqualTo(Integer.toString(expectedBody.getBytes(UTF_8).length));
      }
    } finally {
      ExperimentalSnippetHolder.setSnippet("");
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "setContentLength",
        "setContentLengthLong",
        "setHeader",
        "addHeader",
        "setIntHeader",
        "addIntHeader"
      })
  void acceptedSameLengthDeclarationRetainsReplacementBody(String setter) {
    assumeTrue(isAgentTest());

    ExperimentalSnippetHolder.setSnippet(SAME_LENGTH_RESET_SNIPPET);
    try {
      ServerEndpoint endpoint =
          new ServerEndpoint(
              "SNIPPET_SAME_LENGTH_RESET", "snippetSameLengthReset?setter=" + setter, 200, "");
      AtomicReference<String> contentLength = new AtomicReference<>();
      AggregatedHttpResponse response =
          client
              .execute(request(endpoint, "GET"))
              .peekHeaders(headers -> contentLength.set(headers.get("content-length")))
              .aggregate()
              .join();

      int bodyLength = SAME_LENGTH_RESET_BODY.getBytes(UTF_8).length;
      assertThat(response.contentUtf8()).isEqualTo(SAME_LENGTH_RESET_BODY);
      assertThat(response.contentUtf8()).doesNotContain(SAME_LENGTH_RESET_SNIPPET);
      assertThat(contentLength.get()).isEqualTo(Integer.toString(bodyLength));
    } finally {
      ExperimentalSnippetHolder.setSnippet("");
    }
  }

  public static class SnippetErrorPageInitial extends HttpServlet {
    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {
      response.setContentType("text/html");
      response.setContentLength(ABANDONED_BODY.length() + 1);
      response.getOutputStream().write(ABANDONED_BODY.getBytes(UTF_8));
      if (response.isCommitted()) {
        throw new ServletException("Response committed before error dispatch");
      }
      throw new ServletException("Trigger registered error page");
    }
  }

  public static class SnippetErrorPage extends HttpServlet {
    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response)
        throws IOException {
      response.setContentType("text/html");
      response.getWriter().write(ERROR_BODY);
    }
  }

  public static class SnippetSameLengthReset extends HttpServlet {
    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response)
        throws IOException {
      byte[] replacementBody = SAME_LENGTH_RESET_BODY.getBytes(UTF_8);
      byte[] snippet = SAME_LENGTH_RESET_SNIPPET.getBytes(UTF_8);
      response.setContentType("text/html");
      response.setContentLength(replacementBody.length - snippet.length);
      response.getOutputStream().write("<head>".getBytes(UTF_8));
      setContentLength(response, request.getParameter("setter"), replacementBody.length);
      response.resetBuffer();
      response.getOutputStream().write(replacementBody);
    }

    private static void setContentLength(HttpServletResponse response, String setter, int length) {
      switch (setter) {
        case "setContentLengthLong":
          response.setContentLengthLong(length);
          break;
        case "setHeader":
          response.setHeader("Content-Length", Integer.toString(length));
          break;
        case "addHeader":
          response.addHeader("Content-Length", Integer.toString(length));
          break;
        case "setIntHeader":
          response.setIntHeader("Content-Length", length);
          break;
        case "addIntHeader":
          response.addIntHeader("Content-Length", length);
          break;
        default:
          response.setContentLength(length);
      }
    }
  }
}
