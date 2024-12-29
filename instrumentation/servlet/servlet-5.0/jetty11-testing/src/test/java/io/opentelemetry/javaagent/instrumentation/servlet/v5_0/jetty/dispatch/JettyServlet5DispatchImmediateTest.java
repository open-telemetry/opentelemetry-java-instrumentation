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

import io.opentelemetry.javaagent.instrumentation.servlet.v5_0.TestServlet5;
import jakarta.servlet.Servlet;
import org.eclipse.jetty.servlet.ServletContextHandler;

class JettyServlet5DispatchImmediateTest extends JettyDispatchTest {
  @Override
  public Class<? extends Servlet> servlet() {
    return TestServlet5.Async.class;
  }

  @Override
  public boolean errorEndpointUsesSendError() {
    return false;
  }

  @Override
  protected void setupServlets(ServletContextHandler context) throws Exception {
    super.setupServlets(context);
    addServlet(
        context, "/dispatch" + HTML_PRINT_WRITER.getPath(), TestServlet5.DispatchImmediate.class);
    addServlet(
        context,
        "/dispatch" + HTML_SERVLET_OUTPUT_STREAM.getPath(),
        TestServlet5.DispatchImmediate.class);
    addServlet(context, "/dispatch" + SUCCESS.getPath(), TestServlet5.DispatchImmediate.class);
    addServlet(context, "/dispatch" + QUERY_PARAM.getPath(), TestServlet5.DispatchImmediate.class);
    addServlet(context, "/dispatch" + ERROR.getPath(), TestServlet5.DispatchImmediate.class);
    addServlet(context, "/dispatch" + EXCEPTION.getPath(), TestServlet5.DispatchImmediate.class);
    addServlet(context, "/dispatch" + REDIRECT.getPath(), TestServlet5.DispatchImmediate.class);
    addServlet(
        context, "/dispatch" + AUTH_REQUIRED.getPath(), TestServlet5.DispatchImmediate.class);
    addServlet(
        context, "/dispatch" + CAPTURE_HEADERS.getPath(), TestServlet5.DispatchImmediate.class);
    addServlet(
        context, "/dispatch" + CAPTURE_PARAMETERS.getPath(), TestServlet5.DispatchImmediate.class);
    addServlet(
        context, "/dispatch" + INDEXED_CHILD.getPath(), TestServlet5.DispatchImmediate.class);
    addServlet(context, "/dispatch/recursive", TestServlet5.DispatchRecursive.class);
  }
}
