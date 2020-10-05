/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.ERROR
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.NOT_FOUND
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.QUERY_PARAM
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.SUCCESS
import static io.opentelemetry.trace.Span.Kind.SERVER

import io.opentelemetry.auto.test.asserts.TraceAssert
import io.opentelemetry.auto.test.base.HttpServerTest
import io.opentelemetry.trace.attributes.SemanticAttributes
import javax.servlet.DispatcherType
import javax.servlet.ServletException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Response
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.AbstractHandler
import org.eclipse.jetty.server.handler.ErrorHandler
import spock.lang.Shared

class JettyHandlerTest extends HttpServerTest<Server> {

  @Shared
  ErrorHandler errorHandler = new ErrorHandler() {
    @Override
    protected void handleErrorPage(HttpServletRequest request, Writer writer, int code, String message) throws IOException {
      Throwable th = (Throwable) request.getAttribute("javax.servlet.error.exception")
      message = th ? th.message : message
      if (message) {
        writer.write(message)
      }
    }
  }

  @Shared
  TestHandler testHandler = new TestHandler()

  @Override
  Server startServer(int port) {
    def server = new Server(port)
    server.setHandler(handler())
    server.addBean(errorHandler)
    server.start()
    return server
  }

  AbstractHandler handler() {
    testHandler
  }

  @Override
  void stopServer(Server server) {
    server.stop()
  }

  @Override
  boolean testExceptionBody() {
    false
  }

  static void handleRequest(Request request, HttpServletResponse response) {
    ServerEndpoint endpoint = ServerEndpoint.forPath(request.requestURI)
    controller(endpoint) {
      response.contentType = "text/plain"
      switch (endpoint) {
        case SUCCESS:
          response.status = endpoint.status
          response.writer.print(endpoint.body)
          break
        case QUERY_PARAM:
          response.status = endpoint.status
          response.writer.print(request.queryString)
          break
        case REDIRECT:
          response.sendRedirect(endpoint.body)
          break
        case ERROR:
          response.sendError(endpoint.status, endpoint.body)
          break
        case EXCEPTION:
          throw new Exception(endpoint.body)
        default:
          response.status = NOT_FOUND.status
          response.writer.print(NOT_FOUND.body)
          break
      }
    }
  }

  static class TestHandler extends AbstractHandler {
    @Override
    void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
      //This line here is to verify that we don't break Jetty if it wants to cast to implementation class
      //See https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/1096
      Response jettyResponse = response as Response
      if (baseRequest.dispatcherType != DispatcherType.ERROR) {
        handleRequest(baseRequest, jettyResponse)
        baseRequest.handled = true
      } else {
        errorHandler.handle(target, baseRequest, response, response)
      }
    }
  }

  @Override
  void serverSpan(TraceAssert trace, int index, String traceID = null, String parentID = null, String method = "GET", Long responseContentLength = null, ServerEndpoint endpoint = SUCCESS) {
    trace.span(index) {
      operationName "TestHandler.handle"
      spanKind SERVER
      errored endpoint.errored
      if (endpoint == EXCEPTION) {
        errorEvent(Exception, EXCEPTION.body)
      }
      if (parentID != null) {
        traceId traceID
        parentId parentID
      } else {
        parent()
      }
      attributes {
        "${SemanticAttributes.NET_PEER_IP.key()}" "127.0.0.1"
        "${SemanticAttributes.NET_PEER_PORT.key()}" Long
        "${SemanticAttributes.HTTP_URL.key()}" { it == "${endpoint.resolve(address)}" || it == "${endpoint.resolveWithoutFragment(address)}" }
        "${SemanticAttributes.HTTP_METHOD.key()}" method
        "${SemanticAttributes.HTTP_STATUS_CODE.key()}" endpoint.status
        "${SemanticAttributes.HTTP_FLAVOR.key()}" "HTTP/1.1"
        "${SemanticAttributes.HTTP_USER_AGENT.key()}" TEST_USER_AGENT
        "${SemanticAttributes.HTTP_CLIENT_IP.key()}" TEST_CLIENT_IP
      }
    }
  }
}
