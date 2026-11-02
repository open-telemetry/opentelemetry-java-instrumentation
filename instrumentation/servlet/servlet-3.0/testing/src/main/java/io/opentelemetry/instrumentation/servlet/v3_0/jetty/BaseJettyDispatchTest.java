/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.v3_0.jetty;

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
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.junit.jupiter.params.AfterParameterizedClassInvocation;
import org.junit.jupiter.params.BeforeParameterizedClassInvocation;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.EnumSource;

@ParameterizedClass
@EnumSource(BaseJettyDispatchTest.TestType.class)
public abstract class BaseJettyDispatchTest extends AbstractJettyServlet3Test {

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
}
