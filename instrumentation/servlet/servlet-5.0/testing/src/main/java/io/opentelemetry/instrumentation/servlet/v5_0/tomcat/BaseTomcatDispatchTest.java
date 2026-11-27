/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.v5_0.tomcat;

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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.opentelemetry.instrumentation.servlet.v5_0.RequestDispatcherServlet;
import io.opentelemetry.instrumentation.servlet.v5_0.TestServlet5;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import io.opentelemetry.javaagent.bootstrap.servlet.ExperimentalSnippetHolder;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import jakarta.servlet.Servlet;
import org.apache.catalina.Context;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.AfterParameterizedClassInvocation;
import org.junit.jupiter.params.BeforeParameterizedClassInvocation;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

@ParameterizedClass
@EnumSource(BaseTomcatDispatchTest.TestType.class)
public abstract class BaseTomcatDispatchTest extends AbstractTomcatServlet5Test {
  private static final ServerEndpoint SNIPPET_INCLUDE_SPLIT_HEAD_FIXED_LENGTH =
      new ServerEndpoint(
          "SNIPPET_INCLUDE_SPLIT_HEAD_FIXED_LENGTH", "snippetIncludeSplitHeadFixedLength", 200, "");
  private static final ServerEndpoint SNIPPET_INCLUDE_FIXED_LENGTH_RESET =
      new ServerEndpoint(
          "SNIPPET_INCLUDE_FIXED_LENGTH_RESET", "snippetIncludeFixedLengthReset", 200, "");
  private static final ServerEndpoint SNIPPET_HEADER_FORWARD =
      new ServerEndpoint("SNIPPET_HEADER_FORWARD", "snippetHeaderForward", 200, "");
  private static final ServerEndpoint SNIPPET_HEADER_ASYNC_DISPATCH =
      new ServerEndpoint("SNIPPET_HEADER_ASYNC_DISPATCH", "snippetHeaderAsyncDispatch", 200, "");

  enum TestType {
    DISPATCH_ASYNC {
      @Override
      protected void configure(HttpServerTestOptions options) {
        options.setVerifyServerSpanEndTime(false);
      }

      @Override
      public Class<? extends Servlet> servlet() {
        return TestServlet5.Async.class;
      }

      @Override
      protected void setupServlets(AbstractTomcatServlet5Test test, Context context)
          throws Exception {
        test.addServlet(context, "/dispatch" + SUCCESS.getPath(), TestServlet5.DispatchAsync.class);
        test.addServlet(
            context, "/dispatch" + QUERY_PARAM.getPath(), TestServlet5.DispatchAsync.class);
        test.addServlet(context, "/dispatch" + ERROR.getPath(), TestServlet5.DispatchAsync.class);
        test.addServlet(
            context, "/dispatch" + EXCEPTION.getPath(), TestServlet5.DispatchAsync.class);
        test.addServlet(
            context, "/dispatch" + REDIRECT.getPath(), TestServlet5.DispatchAsync.class);
        test.addServlet(
            context, "/dispatch" + AUTH_REQUIRED.getPath(), TestServlet5.DispatchAsync.class);
        test.addServlet(
            context, "/dispatch" + CAPTURE_HEADERS.getPath(), TestServlet5.DispatchAsync.class);
        test.addServlet(
            context, "/dispatch" + CAPTURE_PARAMETERS.getPath(), TestServlet5.DispatchAsync.class);
        test.addServlet(
            context, "/dispatch" + INDEXED_CHILD.getPath(), TestServlet5.DispatchAsync.class);
        test.addServlet(
            context, "/dispatch" + HTML_PRINT_WRITER.getPath(), TestServlet5.DispatchAsync.class);
        test.addServlet(
            context,
            "/dispatch" + HTML_SERVLET_OUTPUT_STREAM.getPath(),
            TestServlet5.DispatchAsync.class);
        test.addServlet(context, "/dispatch/recursive", TestServlet5.DispatchRecursive.class);
      }

      @Override
      public boolean errorEndpointUsesSendError() {
        return false;
      }

      @Override
      protected boolean assertParentOnRedirect() {
        return false;
      }
    },
    DISPATCH_IMMEDIATE {
      @Override
      void configure(HttpServerTestOptions options) {
        options.setTestNotFound(false);
      }

      @Override
      void setupServlets(AbstractTomcatServlet5Test test, Context context) throws Exception {
        test.addServlet(
            context, "/dispatch" + SUCCESS.getPath(), TestServlet5.DispatchImmediate.class);
        test.addServlet(
            context, "/dispatch" + QUERY_PARAM.getPath(), TestServlet5.DispatchImmediate.class);
        test.addServlet(
            context, "/dispatch" + ERROR.getPath(), TestServlet5.DispatchImmediate.class);
        test.addServlet(
            context, "/dispatch" + EXCEPTION.getPath(), TestServlet5.DispatchImmediate.class);
        test.addServlet(
            context, "/dispatch" + REDIRECT.getPath(), TestServlet5.DispatchImmediate.class);
        test.addServlet(
            context, "/dispatch" + AUTH_REQUIRED.getPath(), TestServlet5.DispatchImmediate.class);
        test.addServlet(
            context, "/dispatch" + CAPTURE_HEADERS.getPath(), TestServlet5.DispatchImmediate.class);
        test.addServlet(
            context,
            "/dispatch" + CAPTURE_PARAMETERS.getPath(),
            TestServlet5.DispatchImmediate.class);
        test.addServlet(
            context, "/dispatch" + INDEXED_CHILD.getPath(), TestServlet5.DispatchImmediate.class);
        test.addServlet(
            context,
            "/dispatch" + HTML_PRINT_WRITER.getPath(),
            TestServlet5.DispatchImmediate.class);
        test.addServlet(
            context,
            "/dispatch" + HTML_SERVLET_OUTPUT_STREAM.getPath(),
            TestServlet5.DispatchImmediate.class);
        test.addServlet(context, "/dispatch/recursive", TestServlet5.DispatchRecursive.class);
      }
    },
    FORWARD {
      @Override
      void configure(HttpServerTestOptions options) {
        options.setTestNotFound(false);
      }

      @Override
      void setupServlets(AbstractTomcatServlet5Test test, Context context) throws Exception {
        test.addServlet(
            context, "/dispatch" + SUCCESS.getPath(), RequestDispatcherServlet.Forward.class);
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
        test.addServlet(
            context,
            "/dispatch" + HTML_PRINT_WRITER.getPath(),
            RequestDispatcherServlet.Forward.class);
        test.addServlet(
            context,
            "/dispatch" + HTML_SERVLET_OUTPUT_STREAM.getPath(),
            RequestDispatcherServlet.Forward.class);
      }
    },
    INCLUDE {
      @Override
      void configure(HttpServerTestOptions options) {
        options.setTestNotFound(false);
        options.setTestRedirect(false);
        options.setTestCaptureHttpHeaders(false);
        options.setTestError(false);
      }

      @Override
      void setupServlets(AbstractTomcatServlet5Test test, Context context) throws Exception {
        test.addServlet(
            context, "/dispatch" + SUCCESS.getPath(), RequestDispatcherServlet.Include.class);
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
            "/dispatch" + HTML_PRINT_WRITER.getPath(),
            RequestDispatcherServlet.Include.class);
        test.addServlet(
            context,
            "/dispatch" + HTML_SERVLET_OUTPUT_STREAM.getPath(),
            RequestDispatcherServlet.Include.class);
        for (String setter :
            new String[] {
              "setContentLength", "setHeader", "addHeader", "setIntHeader", "addIntHeader"
            }) {
          test.addServlet(
              context,
              "/snippetAsyncIncludeSplitHeadFixedLength/" + setter,
              TestServlet5.SnippetAsyncIncludeSplitHeadFixedLength.class);
        }
        test.addServlet(
            context,
            "/snippetIncludeSplitHeadTarget",
            TestServlet5.SnippetIncludeSplitHeadTarget.class);
        test.addServlet(
            context,
            "/snippetIncludeSplitHeadFixedLength",
            TestServlet5.SnippetIncludeSplitHeadFixedLength.class);
        test.addServlet(
            context,
            "/snippetIncludeFixedLengthReset",
            TestServlet5.SnippetIncludeFixedLengthReset.class);
        test.addServlet(
            context,
            "/snippetRegularIncludeFixedLengthResetTarget",
            TestServlet5.SnippetRegularIncludeFixedLengthResetTarget.class);
      }
    };

    abstract void configure(HttpServerTestOptions options);

    abstract void setupServlets(AbstractTomcatServlet5Test test, Context context) throws Exception;

    Class<? extends Servlet> servlet() {
      return TestServlet5.Sync.class;
    }

    boolean errorEndpointUsesSendError() {
      return true;
    }

    boolean assertParentOnRedirect() {
      return true;
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
    testType.configure(options);
  }

  @Override
  public Class<? extends Servlet> servlet() {
    return testType.servlet();
  }

  @Override
  protected void setupServlets(Context context) throws Exception {
    super.setupServlets(context);

    testType.setupServlets(this, context);
    addServlet(context, "/snippetHeaderForward", TestServlet5.SnippetHeaderForward.class);
    addServlet(
        context, "/snippetHeaderAsyncDispatch", TestServlet5.SnippetHeaderAsyncDispatch.class);
    addServlet(
        context, "/snippetHeaderDispatchTarget", TestServlet5.SnippetHeaderDispatchTarget.class);
  }

  @Override
  public boolean errorEndpointUsesSendError() {
    return testType.errorEndpointUsesSendError();
  }

  @Override
  protected boolean assertParentOnRedirect() {
    return testType.assertParentOnRedirect();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"setContentLength", "setHeader", "addHeader", "setIntHeader", "addIntHeader"})
  void fixedLengthSplitHeadIncludeAdjustsWireLength(String setter) {
    assumeTrue(testType == TestType.INCLUDE);
    assumeTrue(isAgentTest());

    String snippet = "<!-- snippet -->";
    ExperimentalSnippetHolder.setSnippet(snippet);
    try {
      AggregatedHttpResponse response =
          client
              .execute(
                  request(
                      new ServerEndpoint(
                          "SNIPPET_INCLUDE_SPLIT_HEAD_FIXED_LENGTH",
                          "snippetAsyncIncludeSplitHeadFixedLength/" + setter,
                          200,
                          ""),
                      "GET"))
              .aggregate()
              .join();
      String expected = "<head>" + snippet + "<title>included</title></head>";

      assertThat(response.contentUtf8()).isEqualTo(expected);
      assertThat(response.contentUtf8()).containsOnlyOnce(snippet);
      assertThat(response.headers().contentLength()).isEqualTo(response.content().length());
    } finally {
      ExperimentalSnippetHolder.setSnippet("");
    }
  }

  @Test
  void fixedLengthSplitHeadRegularIncludeAdjustsWireLength() {
    assumeTrue(testType == TestType.INCLUDE);
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
      assertThat(response.headers().contentLength()).isEqualTo(response.content().length());
    } finally {
      ExperimentalSnippetHolder.setSnippet("");
    }
  }

  @Test
  void fixedLengthResetBufferInRegularIncludeRestoresWireLength() {
    assumeTrue(testType == TestType.INCLUDE);
    assumeTrue(isAgentTest());

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
  void applicationHeadersReachForwardAndAsyncDispatchTargets() {
    assumeTrue(testType == TestType.FORWARD || testType == TestType.DISPATCH_ASYNC);
    assumeTrue(isAgentTest());

    ServerEndpoint endpoint =
        testType == TestType.FORWARD ? SNIPPET_HEADER_FORWARD : SNIPPET_HEADER_ASYNC_DISPATCH;
    String snippet = "<!-- snippet -->";
    ExperimentalSnippetHolder.setSnippet(snippet);
    try {
      AggregatedHttpResponse response = client.execute(request(endpoint, "GET")).aggregate().join();

      assertThat(response.contentUtf8())
          .isEqualTo("<head>" + snippet + "header dispatch target</head>");
      assertThat(response.headers().get("X-Dispatch-Header")).isEqualTo("accepted");
    } finally {
      ExperimentalSnippetHolder.setSnippet("");
    }
  }
}
