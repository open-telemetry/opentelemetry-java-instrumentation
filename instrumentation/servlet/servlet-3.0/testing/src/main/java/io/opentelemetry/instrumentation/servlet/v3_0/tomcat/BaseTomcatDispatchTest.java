/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.v3_0.tomcat;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.AUTH_REQUIRED;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_HEADERS;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_PARAMETERS;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.QUERY_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS;

import io.opentelemetry.instrumentation.servlet.v3_0.RequestDispatcherServlet;
import io.opentelemetry.instrumentation.servlet.v3_0.TestServlet3;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import javax.servlet.Servlet;
import org.apache.catalina.Context;
import org.junit.jupiter.params.AfterParameterizedClassInvocation;
import org.junit.jupiter.params.BeforeParameterizedClassInvocation;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.EnumSource;

@ParameterizedClass
@EnumSource(BaseTomcatDispatchTest.TestType.class)
public abstract class BaseTomcatDispatchTest extends AbstractTomcatServlet3Test {

  enum TestType {
    DISPATCH_ASYNC {
      @Override
      protected void configure(HttpServerTestOptions options) {
        options.setVerifyServerSpanEndTime(false);
      }

      @Override
      public Class<? extends Servlet> servlet() {
        return TestServlet3.Async.class;
      }

      @Override
      protected void setupServlets(AbstractTomcatServlet3Test test, Context context)
          throws Exception {
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
        test.addServlet(
            context, "/dispatch" + HTML_PRINT_WRITER.getPath(), TestServlet3.DispatchAsync.class);
        test.addServlet(
            context,
            "/dispatch" + HTML_SERVLET_OUTPUT_STREAM.getPath(),
            TestServlet3.DispatchAsync.class);
        test.addServlet(context, "/dispatch/recursive", TestServlet3.DispatchRecursive.class);
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
      void setupServlets(AbstractTomcatServlet3Test test, Context context) throws Exception {
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
        test.addServlet(
            context,
            "/dispatch" + HTML_PRINT_WRITER.getPath(),
            TestServlet3.DispatchImmediate.class);
        test.addServlet(
            context,
            "/dispatch" + HTML_SERVLET_OUTPUT_STREAM.getPath(),
            TestServlet3.DispatchImmediate.class);
        test.addServlet(context, "/dispatch/recursive", TestServlet3.DispatchRecursive.class);
      }
    },
    FORWARD {
      @Override
      void configure(HttpServerTestOptions options) {
        options.setTestNotFound(false);
      }

      @Override
      void setupServlets(AbstractTomcatServlet3Test test, Context context) throws Exception {
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
      void setupServlets(AbstractTomcatServlet3Test test, Context context) throws Exception {
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
      }
    };

    abstract void configure(HttpServerTestOptions options);

    abstract void setupServlets(AbstractTomcatServlet3Test test, Context context) throws Exception;

    Class<? extends Servlet> servlet() {
      return TestServlet3.Sync.class;
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
  }

  @Override
  public boolean errorEndpointUsesSendError() {
    return testType.errorEndpointUsesSendError();
  }

  @Override
  protected boolean assertParentOnRedirect() {
    return testType.assertParentOnRedirect();
  }
}
