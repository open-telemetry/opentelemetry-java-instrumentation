/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.v3_0;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.AUTH_REQUIRED;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_HEADERS;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_PARAMETERS;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.QUERY_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.api.internal.HttpConstants;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import io.opentelemetry.javaagent.bootstrap.http.HttpServerResponseCustomizerHolder;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.SpanData;
import javax.servlet.Servlet;

public abstract class AbstractServlet3Test<SERVER, CONTEXT> extends AbstractHttpServerTest<SERVER> {

  public static final ServerEndpoint HTML_PRINT_WRITER =
      new ServerEndpoint(
          "HTML_PRINT_WRITER",
          "htmlPrintWriter",
          200,
          "<!DOCTYPE html>\n"
              + "<html lang=\"en\">\n"
              + "<head>\n"
              + "  <meta charset=\"UTF-8\">\n"
              + "  <title>Title</title>\n"
              + "</head>\n"
              + "<body>\n"
              + "<p>test works</p>\n"
              + "</body>\n"
              + "</html>");
  public static final ServerEndpoint HTML_SERVLET_OUTPUT_STREAM =
      new ServerEndpoint(
          "HTML_SERVLET_OUTPUT_STREAM",
          "htmlServletOutputStream",
          200,
          "<!DOCTYPE html>\n"
              + "<html lang=\"en\">\n"
              + "<head>\n"
              + "  <meta charset=\"UTF-8\">\n"
              + "  <title>Title</title>\n"
              + "</head>\n"
              + "<body>\n"
              + "<p>test works</p>\n"
              + "</body>\n"
              + "</html>");

  @Override
  protected void configure(HttpServerTestOptions options) {
    super.configure(options);
    options.setTestCaptureRequestParameters(false); // Requires AgentConfig.
    options.setTestCaptureHttpHeaders(false); // Requires AgentConfig.
    options.disableTestNonStandardHttpMethod(); // test doesn't use route mapping correctly.
    options.setTestException(false); // filters don't have visibility into exception handling above.
    HttpServerResponseCustomizerHolder.setCustomizer(new TestAgentHttpResponseCustomizer());
    options.setHasResponseCustomizer(e -> true);
    options.setHasResponseSpan(this::hasResponseSpan);
  }

  protected boolean hasResponseSpan(ServerEndpoint endpoint) {
    return endpoint.equals(REDIRECT) || (endpoint.equals(ERROR) && errorEndpointUsesSendError());
  }

  public abstract Class<? extends Servlet> servlet();

  public abstract void addServlet(CONTEXT context, String path, Class<? extends Servlet> servlet)
      throws Exception;

  protected void setupServlets(CONTEXT context) throws Exception {
    Class<? extends Servlet> servlet = servlet();

    addServlet(context, SUCCESS.getPath(), servlet);
    addServlet(context, QUERY_PARAM.getPath(), servlet);
    addServlet(context, ERROR.getPath(), servlet);
    addServlet(context, EXCEPTION.getPath(), servlet);
    addServlet(context, REDIRECT.getPath(), servlet);
    addServlet(context, AUTH_REQUIRED.getPath(), servlet);
    addServlet(context, INDEXED_CHILD.getPath(), servlet);
    addServlet(context, CAPTURE_HEADERS.getPath(), servlet);
    addServlet(context, CAPTURE_PARAMETERS.getPath(), servlet);
    addServlet(context, HTML_PRINT_WRITER.getPath(), servlet);
    addServlet(context, HTML_SERVLET_OUTPUT_STREAM.getPath(), servlet);
  }

  @Override
  public String expectedHttpRoute(ServerEndpoint endpoint, String method) {
    // no need to compute route if we're not expecting it
    if (!hasHttpRouteAttribute(endpoint)) {
      return null;
    }

    if (method.equals(HttpConstants._OTHER)) {
      return getContextPath() + endpoint.getPath();
    }

    // NOTE: Primary difference from javaagent servlet instrumentation!
    // Since just we're working with a filter, we can't actually get the proper servlet path.
    return getContextPath() + "/*";
  }

  public boolean errorEndpointUsesSendError() {
    return true;
  }

  @Override
  protected SpanDataAssert assertResponseSpan(
      SpanDataAssert span, SpanData parentSpan, String method, ServerEndpoint endpoint) {
    switch (endpoint.name()) {
      case "REDIRECT":
        SpanDataAssert spanDataAssert =
            span.satisfies(s -> assertThat(s.getName()).matches(".*\\.sendRedirect"))
                .hasKind(SpanKind.INTERNAL);
        if (assertParentOnRedirect()) {
          return spanDataAssert.hasParent(parentSpan);
        }
        return spanDataAssert;
      case "ERROR":
        return span.satisfies(s -> assertThat(s.getName()).matches(".*\\.sendError"))
            .hasKind(SpanKind.INTERNAL)
            .hasParent(parentSpan);
      default:
        break;
    }
    return span;
  }

  protected boolean assertParentOnRedirect() {
    return true;
  }
}
