/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0.tomcat.dispatch;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.AUTH_REQUIRED;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_HEADERS;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_PARAMETERS;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.QUERY_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.javaagent.instrumentation.servlet.v3_0.tomcat.TestServlet3;
import javax.servlet.Servlet;
import org.apache.catalina.Context;
import org.junit.jupiter.api.extension.RegisterExtension;

class TomcatServlet3DispatchAsyncTest extends TomcatDispatchTest {

  @RegisterExtension
  protected static final InstrumentationExtension testing =
      HttpServerInstrumentationExtension.forAgent();

  public TomcatServlet3DispatchAsyncTest() {
    super(testing);
  }

  @Override
  public Class<? extends Servlet> servlet() {
    return TestServlet3.Async.class;
  }

  @Override
  protected void setupServlets(Context context) {
    super.setupServlets(context);

    addServlet(context, "/dispatch" + SUCCESS.getPath(), TestServlet3.DispatchAsync.class);
    addServlet(context, "/dispatch" + QUERY_PARAM.getPath(), TestServlet3.DispatchAsync.class);
    addServlet(context, "/dispatch" + ERROR.getPath(), TestServlet3.DispatchAsync.class);
    addServlet(context, "/dispatch" + EXCEPTION.getPath(), TestServlet3.DispatchAsync.class);
    addServlet(context, "/dispatch" + REDIRECT.getPath(), TestServlet3.DispatchAsync.class);
    addServlet(context, "/dispatch" + AUTH_REQUIRED.getPath(), TestServlet3.DispatchAsync.class);
    addServlet(context, "/dispatch" + CAPTURE_HEADERS.getPath(), TestServlet3.DispatchAsync.class);
    addServlet(
        context, "/dispatch" + CAPTURE_PARAMETERS.getPath(), TestServlet3.DispatchAsync.class);
    addServlet(context, "/dispatch" + INDEXED_CHILD.getPath(), TestServlet3.DispatchAsync.class);
    addServlet(
        context, "/dispatch" + HTML_PRINT_WRITER.getPath(), TestServlet3.DispatchAsync.class);
    addServlet(
        context,
        "/dispatch" + HTML_SERVLET_OUTPUT_STREAM.getPath(),
        TestServlet3.DispatchAsync.class);
    addServlet(context, "/dispatch/recursive", TestServlet3.DispatchRecursive.class);
  }

  @Override
  public boolean errorEndpointUsesSendError() {
    return false;
  }

  @Override
  protected boolean assertParentOnRedirect() {
    return false;
  }
}
