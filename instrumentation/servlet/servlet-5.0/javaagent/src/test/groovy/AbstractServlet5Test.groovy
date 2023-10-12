/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.api.internal.HttpConstants
import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.instrumentation.test.base.HttpServerTest
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint
import io.opentelemetry.javaagent.bootstrap.servlet.ExperimentalSnippetHolder
import io.opentelemetry.semconv.SemanticAttributes
import jakarta.servlet.Servlet

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.AUTH_REQUIRED
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_HEADERS
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_PARAMETERS
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.NOT_FOUND
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.QUERY_PARAM
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS

abstract class AbstractServlet5Test<SERVER, CONTEXT> extends HttpServerTest<SERVER> implements AgentTestTrait {
  @Override
  URI buildAddress() {
    return new URI("http://localhost:$port$contextPath/")
  }

  // FIXME: Add authentication tests back in...
//  @Shared
//  protected String user = "user"
//  @Shared
//  protected String pass = "password"

  abstract Class<Servlet> servlet()

  abstract void addServlet(CONTEXT context, String path, Class<Servlet> servlet)

  public static final ServerEndpoint HTML_PRINT_WRITER =
    new ServerEndpoint("HTML_PRINT_WRITER",  "htmlPrintWriter",
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
        + "</html>")
  public static final ServerEndpoint HTML_SERVLET_OUTPUT_STREAM =
    new ServerEndpoint("HTML_SERVLET_OUTPUT_STREAM", "htmlServletOutputStream",
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
        + "</html>")

  protected void setupServlets(CONTEXT context) {
    def servlet = servlet()

    addServlet(context, SUCCESS.path, servlet)
    addServlet(context, QUERY_PARAM.path, servlet)
    addServlet(context, ERROR.path, servlet)
    addServlet(context, EXCEPTION.path, servlet)
    addServlet(context, REDIRECT.path, servlet)
    addServlet(context, AUTH_REQUIRED.path, servlet)
    addServlet(context, INDEXED_CHILD.path, servlet)
    addServlet(context, CAPTURE_HEADERS.path, servlet)
    addServlet(context, CAPTURE_PARAMETERS.path, servlet)
    addServlet(context, HTML_PRINT_WRITER.path, servlet)
    addServlet(context, HTML_SERVLET_OUTPUT_STREAM.path, servlet)
  }

  @Override
  boolean testCapturedRequestParameters() {
    true
  }

  boolean errorEndpointUsesSendError() {
    true
  }

  @Override
  String expectedHttpRoute(ServerEndpoint endpoint, String method) {
    // no need to compute route if we're not expecting it
    if (!httpAttributes(endpoint).contains(SemanticAttributes.HTTP_ROUTE)) {
      return null
    }
    if (method == HttpConstants._OTHER) {
      return endpoint.resolvePath(address).path
    }
    switch (endpoint) {
      case NOT_FOUND:
        return getContextPath() + "/*"
      default:
        return super.expectedHttpRoute(endpoint, method)
    }
  }

  @Override
  boolean hasResponseCustomizer(ServerEndpoint endpoint) {
    true
  }

  @Override
  boolean hasResponseSpan(ServerEndpoint endpoint) {
    endpoint == REDIRECT || (endpoint == ERROR && errorEndpointUsesSendError())
  }

  @Override
  void responseSpan(TraceAssert trace, int index, Object parent, String method, ServerEndpoint endpoint) {
    switch (endpoint) {
      case REDIRECT:
        redirectSpan(trace, index, parent)
        break
      case ERROR:
        sendErrorSpan(trace, index, parent)
        break
    }
  }

  def "snippet injection with ServletOutputStream"() {
    setup:
    ExperimentalSnippetHolder.setSnippet("\n  <script type=\"text/javascript\"> Test </script>")
    def request = request(HTML_SERVLET_OUTPUT_STREAM, "GET")
    def response = client.execute(request).aggregate().join()

    expect:
    response.status().code() == HTML_SERVLET_OUTPUT_STREAM.status
    String result = "<!DOCTYPE html>\n" +
      "<html lang=\"en\">\n" +
      "<head>\n" +
      "  <script type=\"text/javascript\"> Test </script>\n" +
      "  <meta charset=\"UTF-8\">\n" +
      "  <title>Title</title>\n" +
      "</head>\n" +
      "<body>\n" +
      "<p>test works</p>\n" +
      "</body>\n" +
      "</html>"
    response.contentUtf8() == result
    response.headers().contentLength() == result.length()

    cleanup:
    ExperimentalSnippetHolder.setSnippet("")

    def expectedRoute = expectedHttpRoute(HTML_SERVLET_OUTPUT_STREAM, "GET")
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "GET" + (expectedRoute != null ? " " + expectedRoute : "")
          kind SpanKind.SERVER
          hasNoParent()
        }
        span(1) {
          name "controller"
          kind SpanKind.INTERNAL
          childOf span(0)
        }
      }
    }
  }

  def "snippet injection with PrintWriter"() {
    setup:
    ExperimentalSnippetHolder.setSnippet("\n  <script type=\"text/javascript\"> Test2 </script>")
    def request = request(HTML_PRINT_WRITER, "GET")
    def response = client.execute(request).aggregate().join()

    expect:
    response.status().code() == HTML_PRINT_WRITER.status
    String result = "<!DOCTYPE html>\n" +
      "<html lang=\"en\">\n" +
      "<head>\n" +
      "  <script type=\"text/javascript\"> Test2 </script>\n" +
      "  <meta charset=\"UTF-8\">\n" +
      "  <title>Title</title>\n" +
      "</head>\n" +
      "<body>\n" +
      "<p>test works</p>\n" +
      "</body>\n" +
      "</html>"

    response.contentUtf8() == result
    response.headers().contentLength() == result.length()

    cleanup:
    ExperimentalSnippetHolder.setSnippet("")

    def expectedRoute = expectedHttpRoute(HTML_PRINT_WRITER, "GET")
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "GET" + (expectedRoute != null ? " " + expectedRoute : "")
          kind SpanKind.SERVER
          hasNoParent()
        }
        span(1) {
          name "controller"
          kind SpanKind.INTERNAL
          childOf span(0)
        }
      }
    }
  }
}
