/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.v3_0.jetty;

import static io.opentelemetry.instrumentation.servlet.v3_0.AbstractServlet3Test.HTML_PRINT_WRITER;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.AUTH_REQUIRED;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_HEADERS;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_PARAMETERS;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.QUERY_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS;
import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.opentelemetry.instrumentation.servlet.v3_0.RequestDispatcherServlet;
import io.opentelemetry.instrumentation.servlet.v3_0.TestServlet3;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import io.opentelemetry.javaagent.bootstrap.servlet.ExperimentalSnippetHolder;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import javax.servlet.Servlet;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.AfterParameterizedClassInvocation;
import org.junit.jupiter.params.BeforeParameterizedClassInvocation;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.EnumSource;

@ParameterizedClass
@EnumSource(BaseJettyDispatchTest.TestType.class)
public abstract class BaseJettyDispatchTest extends AbstractJettyServlet3Test {
  private static final ServerEndpoint SNIPPET_FORWARD =
      new ServerEndpoint("SNIPPET_FORWARD", "snippetForward", 200, "");
  private static final ServerEndpoint SNIPPET_FORWARD_21 =
      new ServerEndpoint("SNIPPET_FORWARD_21", "snippetForward?contentLength=21", 200, "", false);
  private static final ServerEndpoint SNIPPET_FORWARD_22 =
      new ServerEndpoint("SNIPPET_FORWARD_22", "snippetForward?contentLength=22", 200, "", false);
  private static final ServerEndpoint SNIPPET_CACHED_WRITER_FORWARD =
      new ServerEndpoint("SNIPPET_CACHED_WRITER_FORWARD", "snippetCachedWriterForward", 200, "");
  private static final ServerEndpoint SNIPPET_ASYNC_RESET =
      new ServerEndpoint("SNIPPET_ASYNC_RESET", "snippetAsyncReset", 200, "");
  private static final ServerEndpoint SNIPPET_ASYNC_LENGTH =
      new ServerEndpoint("SNIPPET_ASYNC_LENGTH", "snippetAsyncContentLength", 200, "");
  private static final ServerEndpoint SNIPPET_ASYNC_LENGTH_CLEAR =
      new ServerEndpoint(
          "SNIPPET_ASYNC_LENGTH_CLEAR", "snippetAsyncContentLength?length=clear", 200, "", false);
  private static final ServerEndpoint SNIPPET_REJECTED_FORWARD =
      new ServerEndpoint("SNIPPET_REJECTED_FORWARD", "snippetRejectedForward", 200, "");
  private static final ServerEndpoint SNIPPET_WRITER_ACQUISITION_FAILURE =
      new ServerEndpoint(
          "SNIPPET_WRITER_ACQUISITION_FAILURE", "snippetWriterAcquisitionFailure", 200, "");
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

  enum TestType {
    DISPATCH_ASYNC {
      @Override
      Class<? extends Servlet> servlet() {
        return TestServlet3.Async.class;
      }

      @Override
      boolean isAsyncTest() {
        return true;
      }

      @Override
      void setupServlets(AbstractJettyServlet3Test test, ServletContextHandler context)
          throws Exception {
        test.addServlet(
            context, "/dispatch" + HTML_PRINT_WRITER.getPath(), TestServlet3.DispatchAsync.class);
        test.addServlet(
            context,
            "/dispatch" + HTML_SERVLET_OUTPUT_STREAM.getPath(),
            TestServlet3.DispatchAsync.class);
        test.addServlet(context, "/dispatch" + SUCCESS.getPath(), TestServlet3.DispatchAsync.class);
        test.addServlet(
            context, "/dispatch" + QUERY_PARAM.getPath(), TestServlet3.DispatchAsync.class);
        test.addServlet(context, "/dispatch" + ERROR.getPath(), TestServlet3.DispatchAsync.class);
        test.addServlet(
            context, "/dispatch" + EXCEPTION.getPath(), TestServlet3.DispatchAsync.class);
        test.addServlet(
            context, "/dispatch" + REDIRECT.getPath(), TestServlet3.DispatchAsync.class);
        test.addServlet(
            context, "/dispatch" + AUTH_REQUIRED.getPath(), TestServlet3.DispatchAsync.class);
        test.addServlet(
            context, "/dispatch" + CAPTURE_HEADERS.getPath(), TestServlet3.DispatchAsync.class);
        test.addServlet(
            context, "/dispatch" + CAPTURE_PARAMETERS.getPath(), TestServlet3.DispatchAsync.class);
        test.addServlet(
            context, "/dispatch" + INDEXED_CHILD.getPath(), TestServlet3.DispatchAsync.class);
        test.addServlet(context, "/dispatch/recursive", TestServlet3.DispatchRecursive.class);
      }

      @Override
      boolean errorEndpointUsesSendError() {
        return false;
      }
    },
    DISPATCH_IMMEDIATE {
      @Override
      Class<? extends Servlet> servlet() {
        return TestServlet3.Async.class;
      }

      @Override
      boolean isAsyncTest() {
        return true;
      }

      @Override
      void setupServlets(AbstractJettyServlet3Test test, ServletContextHandler context)
          throws Exception {
        test.addServlet(
            context,
            "/dispatch" + HTML_PRINT_WRITER.getPath(),
            TestServlet3.DispatchImmediate.class);
        test.addServlet(
            context,
            "/dispatch" + HTML_SERVLET_OUTPUT_STREAM.getPath(),
            TestServlet3.DispatchImmediate.class);
        test.addServlet(
            context, "/dispatch" + SUCCESS.getPath(), TestServlet3.DispatchImmediate.class);
        test.addServlet(
            context, "/dispatch" + QUERY_PARAM.getPath(), TestServlet3.DispatchImmediate.class);
        test.addServlet(
            context, "/dispatch" + ERROR.getPath(), TestServlet3.DispatchImmediate.class);
        test.addServlet(
            context, "/dispatch" + EXCEPTION.getPath(), TestServlet3.DispatchImmediate.class);
        test.addServlet(
            context, "/dispatch" + REDIRECT.getPath(), TestServlet3.DispatchImmediate.class);
        test.addServlet(
            context, "/dispatch" + AUTH_REQUIRED.getPath(), TestServlet3.DispatchImmediate.class);
        test.addServlet(
            context, "/dispatch" + CAPTURE_HEADERS.getPath(), TestServlet3.DispatchImmediate.class);
        test.addServlet(
            context,
            "/dispatch" + CAPTURE_PARAMETERS.getPath(),
            TestServlet3.DispatchImmediate.class);
        test.addServlet(
            context, "/dispatch" + INDEXED_CHILD.getPath(), TestServlet3.DispatchImmediate.class);
        test.addServlet(context, "/dispatch/recursive", TestServlet3.DispatchRecursive.class);
      }

      @Override
      boolean errorEndpointUsesSendError() {
        return false;
      }
    },
    FORWARD {
      @Override
      void setupServlets(AbstractJettyServlet3Test test, ServletContextHandler context)
          throws Exception {
        test.addServlet(
            context, "/dispatch" + SUCCESS.getPath(), RequestDispatcherServlet.Forward.class);
        test.addServlet(
            context,
            "/dispatch" + HTML_PRINT_WRITER.getPath(),
            RequestDispatcherServlet.Forward.class);
        test.addServlet(
            context,
            "/dispatch" + HTML_SERVLET_OUTPUT_STREAM.getPath(),
            RequestDispatcherServlet.Forward.class);
        test.addServlet(context, "/snippetForward", TestServlet3.SnippetForward.class);
        test.addServlet(
            context, "/snippetCachedWriterForward", TestServlet3.SnippetCachedWriterForward.class);
        test.addServlet(
            context, "/snippetCachedWriterTarget", TestServlet3.SnippetCachedWriterTarget.class);
        test.addServlet(context, "/snippetAsyncReset", TestServlet3.SnippetAsyncReset.class);
        test.addServlet(
            context, "/snippetAsyncContentLength", TestServlet3.SnippetAsyncContentLength.class);
        test.addServlet(
            context,
            "/snippetAsyncContentLengthTarget",
            TestServlet3.SnippetAsyncContentLengthTarget.class);
        test.addServlet(
            context, "/snippetRejectedForward", TestServlet3.SnippetRejectedForward.class);
        test.addServlet(
            context,
            "/snippetWriterAcquisitionFailure",
            TestServlet3.SnippetWriterAcquisitionFailure.class);
        test.addServlet(
            context, "/dispatch" + QUERY_PARAM.getPath(), RequestDispatcherServlet.Forward.class);
        test.addServlet(
            context, "/dispatch" + REDIRECT.getPath(), RequestDispatcherServlet.Forward.class);
        test.addServlet(
            context, "/dispatch" + ERROR.getPath(), RequestDispatcherServlet.Forward.class);
        test.addServlet(
            context, "/dispatch" + EXCEPTION.getPath(), RequestDispatcherServlet.Forward.class);
        test.addServlet(
            context, "/dispatch" + AUTH_REQUIRED.getPath(), RequestDispatcherServlet.Forward.class);
        test.addServlet(
            context,
            "/dispatch" + CAPTURE_HEADERS.getPath(),
            RequestDispatcherServlet.Forward.class);
        test.addServlet(
            context,
            "/dispatch" + CAPTURE_PARAMETERS.getPath(),
            RequestDispatcherServlet.Forward.class);
        test.addServlet(
            context, "/dispatch" + INDEXED_CHILD.getPath(), RequestDispatcherServlet.Forward.class);
      }
    },
    INCLUDE {
      @Override
      void configure(HttpServerTestOptions options) {
        options.setTestRedirect(false);
        options.setTestCaptureHttpHeaders(false);
        options.setTestError(false);
      }

      @Override
      void setupServlets(AbstractJettyServlet3Test test, ServletContextHandler context)
          throws Exception {
        test.addServlet(
            context, "/dispatch" + SUCCESS.getPath(), RequestDispatcherServlet.Include.class);
        test.addServlet(
            context,
            "/dispatch" + HTML_PRINT_WRITER.getPath(),
            RequestDispatcherServlet.Include.class);
        test.addServlet(
            context,
            "/dispatch" + HTML_SERVLET_OUTPUT_STREAM.getPath(),
            RequestDispatcherServlet.Include.class);
        test.addServlet(
            context, "/dispatch" + QUERY_PARAM.getPath(), RequestDispatcherServlet.Include.class);
        test.addServlet(
            context, "/dispatch" + REDIRECT.getPath(), RequestDispatcherServlet.Include.class);
        test.addServlet(
            context, "/dispatch" + ERROR.getPath(), RequestDispatcherServlet.Include.class);
        test.addServlet(
            context, "/dispatch" + EXCEPTION.getPath(), RequestDispatcherServlet.Include.class);
        test.addServlet(
            context, "/dispatch" + AUTH_REQUIRED.getPath(), RequestDispatcherServlet.Include.class);
        test.addServlet(
            context,
            "/dispatch" + CAPTURE_PARAMETERS.getPath(),
            RequestDispatcherServlet.Include.class);
        test.addServlet(
            context, "/dispatch" + INDEXED_CHILD.getPath(), RequestDispatcherServlet.Include.class);
        test.addServlet(
            context,
            "/snippetAsyncIncludeRawReset",
            TestServlet3.SnippetAsyncIncludeRawReset.class);
        test.addServlet(
            context,
            "/snippetIncludeRawResetTarget",
            TestServlet3.SnippetIncludeRawResetTarget.class);
        test.addServlet(
            context,
            "/snippetAsyncIncludeFixedLengthReset",
            TestServlet3.SnippetAsyncIncludeFixedLengthReset.class);
        test.addServlet(
            context,
            "/snippetIncludeFixedLengthResetTarget",
            TestServlet3.SnippetIncludeFixedLengthResetTarget.class);
        test.addServlet(
            context,
            "/snippetAsyncIncludeSplitHeadFixedLength/setContentLength",
            TestServlet3.SnippetAsyncIncludeSplitHeadFixedLength.class);
        test.addServlet(
            context,
            "/snippetIncludeSplitHeadTarget",
            TestServlet3.SnippetIncludeSplitHeadTarget.class);
        test.addServlet(
            context,
            "/snippetContentLengthRemoval/setHeader",
            TestServlet3.SnippetContentLengthRemoval.class);
        test.addServlet(
            context,
            "/snippetContentLengthRemoval/setContentLength",
            TestServlet3.SnippetContentLengthRemoval.class);
      }
    };

    abstract void setupServlets(AbstractJettyServlet3Test test, ServletContextHandler context)
        throws Exception;

    void configure(HttpServerTestOptions options) {}

    Class<? extends Servlet> servlet() {
      return TestServlet3.Sync.class;
    }

    boolean errorEndpointUsesSendError() {
      return true;
    }

    boolean isAsyncTest() {
      return false;
    }
  }

  @Parameter private TestType testType;

  @BeforeParameterizedClassInvocation
  @Override
  protected void setupOptions() {
    super.setupOptions();
  }

  @AfterParameterizedClassInvocation
  void cleanup() {
    cleanupServer();
  }

  @Override
  protected void configure(HttpServerTestOptions options) {
    super.configure(options);
    options.setContextPath(getContextPath() + "/dispatch");

    testType.configure(options);
  }

  @Override
  public Class<? extends Servlet> servlet() {
    return testType.servlet();
  }

  @Override
  protected void setupServlets(ServletContextHandler context) throws Exception {
    super.setupServlets(context);

    testType.setupServlets(this, context);
  }

  @Override
  public boolean errorEndpointUsesSendError() {
    return testType.errorEndpointUsesSendError();
  }

  @Override
  public boolean isAsyncTest() {
    return testType.isAsyncTest();
  }

  @Test
  void asyncRawContentLengthChanges() {
    assumeTrue(testType == TestType.FORWARD);
    assumeTrue(isAgentTest());
    assumeTrue(Server.getVersion().startsWith("10."));
    assertAsyncContentLength(SNIPPET_ASYNC_LENGTH, true);
    assertAsyncContentLength(SNIPPET_ASYNC_LENGTH_CLEAR, false);
  }

  private void assertAsyncContentLength(ServerEndpoint endpoint, boolean fixedLength) {
    String snippet = "<!-- snippet -->";
    ExperimentalSnippetHolder.setSnippet(snippet);
    try {
      AggregatedHttpResponse response = client.execute(request(endpoint, "GET")).aggregate().join();
      assertThat(response.status().code()).isEqualTo(200);
      assertThat(response.contentUtf8()).isEqualTo("<head>" + snippet + "async response</head>");
      assertThat(response.contentUtf8()).containsOnlyOnce(snippet);
      if (fixedLength || response.headers().contentLength() >= 0) {
        assertThat(response.headers().contentLength()).isEqualTo(response.content().length());
      }
    } finally {
      ExperimentalSnippetHolder.setSnippet("");
    }
  }

  @Test
  void forwardDiscardsUncommittedSnippetBody() {
    assumeTrue(testType == TestType.FORWARD);
    assumeTrue(isAgentTest());
    assertReplacementBody(SNIPPET_FORWARD);
  }

  @Test
  void forwardDiscardsUncommittedSnippetBodyNearContentLengthBoundaries() {
    assumeTrue(testType == TestType.FORWARD);
    assumeTrue(isAgentTest());
    assertReplacementBody(SNIPPET_FORWARD_21);
    assertReplacementBody(SNIPPET_FORWARD_22);
  }

  @Test
  void cachedWriterRemainsOwnedAfterForward() {
    assumeTrue(testType == TestType.FORWARD);
    assumeTrue(isAgentTest());
    assertReplacementBody(SNIPPET_CACHED_WRITER_FORWARD);
  }

  @Test
  void asyncRawResetBufferDiscardsUncommittedSnippetBody() {
    assumeTrue(testType == TestType.FORWARD);
    assumeTrue(isAgentTest());
    assertReplacementBody(SNIPPET_ASYNC_RESET);
  }

  @Test
  void rejectedCommittedForwardRetainsInjectedMarker() {
    assumeTrue(testType == TestType.FORWARD);
    assumeTrue(isAgentTest());

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
        assertThat(response.headers().contentLength()).isEqualTo(response.content().length());
      }
    } finally {
      ExperimentalSnippetHolder.setSnippet("");
    }
  }

  @Test
  void failedWriterAcquisitionDoesNotDisableStreamInjection() {
    assumeTrue(testType == TestType.FORWARD);
    assumeTrue(isAgentTest());
    assertReplacementBody(SNIPPET_WRITER_ACQUISITION_FAILURE);
  }

  @Test
  void rawResponseResetDuringIncludeDiscardsBodyAndAllowsInjection() {
    assumeTrue(testType == TestType.INCLUDE);
    assumeTrue(testLatestDeps());
    assumeTrue(isAgentTest());
    assertReplacementBody(SNIPPET_INCLUDE_RAW_RESET);
  }

  @Test
  void fixedLengthRawResetDuringIncludeRestoresDeclaredLength() {
    assumeTrue(testType == TestType.INCLUDE);
    assumeTrue(testLatestDeps());
    assumeTrue(isAgentTest());

    String snippet = "<!-- snippet -->";
    ExperimentalSnippetHolder.setSnippet(snippet);
    try {
      AggregatedHttpResponse response =
          client.execute(request(SNIPPET_INCLUDE_FIXED_LENGTH_RESET, "GET")).aggregate().join();

      assertThat(response.status().code()).isEqualTo(200);
      assertThat(response.contentUtf8()).isEqualTo(TestServlet3.SNIPPET_RESET_REPLACEMENT_BODY);
      assertThat(response.contentUtf8()).doesNotContain(snippet);
      assertThat(response.headers().contentLength()).isEqualTo(response.content().length());
    } finally {
      ExperimentalSnippetHolder.setSnippet("");
    }
  }

  @Test
  void fixedLengthSplitHeadIncludeUsesJettyPrefixedHeaders() {
    assumeTrue(testType == TestType.INCLUDE);
    assumeTrue(testLatestDeps());
    assumeTrue(isAgentTest());

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
    assumeTrue(testType == TestType.INCLUDE);
    assumeTrue(testLatestDeps());
    assumeTrue(isAgentTest());

    for (ServerEndpoint endpoint :
        new ServerEndpoint[] {
          SNIPPET_CONTENT_LENGTH_REMOVAL_HEADER, SNIPPET_CONTENT_LENGTH_REMOVAL_NUMERIC
        }) {
      String snippet = "<!-- snippet -->";
      ExperimentalSnippetHolder.setSnippet(snippet);
      try {
        AggregatedHttpResponse response =
            client.execute(request(endpoint, "GET")).aggregate().join();

        assertThat(response.contentUtf8()).isEqualTo(TestServlet3.SNIPPET_RESET_REPLACEMENT_BODY);
        assertThat(response.contentUtf8()).doesNotContain(snippet);
        if (response.headers().contentLength() >= 0) {
          assertThat(response.headers().contentLength()).isEqualTo(response.content().length());
        }
      } finally {
        ExperimentalSnippetHolder.setSnippet("");
      }
    }
  }

  private void assertReplacementBody(ServerEndpoint source) {
    String snippet = "<!-- snippet -->";
    ExperimentalSnippetHolder.setSnippet(snippet);
    try {
      AggregatedHttpResponse response = client.execute(request(source, "GET")).aggregate().join();
      String expected = HTML_PRINT_WRITER.getBody().replace("<head>", "<head>" + snippet);
      assertThat(response.contentUtf8()).isEqualTo(expected);
      assertThat(response.contentUtf8()).containsOnlyOnce(snippet);
      assertThat(response.contentUtf8()).doesNotContain("discarded-prefix");
      assertThat(response.headers().contentLength()).isEqualTo(response.content().length());
    } finally {
      ExperimentalSnippetHolder.setSnippet("");
    }
  }
}
