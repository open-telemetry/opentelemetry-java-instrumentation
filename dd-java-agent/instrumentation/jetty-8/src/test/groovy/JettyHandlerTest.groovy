import datadog.trace.agent.test.asserts.TraceAssert
import datadog.trace.agent.test.base.HttpServerTest
import datadog.trace.api.DDSpanTypes
import datadog.trace.instrumentation.jetty8.JettyDecorator
import io.opentracing.tag.Tags
import javax.servlet.DispatcherType
import javax.servlet.ServletException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.AbstractHandler
import org.eclipse.jetty.server.handler.ErrorHandler

import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.ERROR
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.NOT_FOUND
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.SUCCESS

class JettyHandlerTest extends HttpServerTest<Server, JettyDecorator> {

  static {
    System.setProperty("dd.integration.jetty.enabled", "true")
  }

  static errorHandler = new ErrorHandler() {
    @Override
    protected void handleErrorPage(HttpServletRequest request, Writer writer, int code, String message) throws IOException {
      Throwable th = (Throwable) request.getAttribute("javax.servlet.error.exception")
      message = th ? th.message : message
      if (message) {
        writer.write(message)
      }
    }
  }

  @Override
  Server startServer(int port) {
    def server = new Server(port)
    server.setHandler(handler())
    server.addBean(errorHandler)
    server.start()
    return server
  }

  AbstractHandler handler() {
    TestHandler.INSTANCE
  }

  @Override
  void stopServer(Server server) {
    server.stop()
  }

  @Override
  JettyDecorator decorator() {
    return JettyDecorator.DECORATE
  }

  @Override
  String expectedOperationName() {
    return "jetty.request"
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
    static final TestHandler INSTANCE = new TestHandler()

    @Override
    void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
      if (baseRequest.dispatcherType != DispatcherType.ERROR) {
        handleRequest(baseRequest, response)
        baseRequest.handled = true
      } else {
        errorHandler.handle(target, baseRequest, response, response)
      }
    }
  }

  @Override
  void serverSpan(TraceAssert trace, int index, String traceID = null, String parentID = null, String method = "GET", ServerEndpoint endpoint = SUCCESS) {
    def handlerName = handler().class.name
    trace.span(index) {
      serviceName expectedServiceName()
      operationName expectedOperationName()
      resourceName endpoint.status == 404 ? "404" : "$method $handlerName"
      spanType DDSpanTypes.HTTP_SERVER
      errored endpoint.errored
      if (parentID != null) {
        traceId traceID
        parentId parentID
      } else {
        parent()
      }
      tags {
        "span.origin.type" handlerName
        defaultTags(true)
        "$Tags.COMPONENT.key" serverDecorator.component()
        if (endpoint.errored) {
          "$Tags.ERROR.key" endpoint.errored
          "error.msg" { it == null || it == EXCEPTION.body }
          "error.type" { it == null || it == Exception.name }
          "error.stack" { it == null || it instanceof String }
        }
        "$Tags.HTTP_STATUS.key" endpoint.status
        "$Tags.HTTP_URL.key" "${endpoint.resolve(address)}"
        "$Tags.PEER_HOSTNAME.key" { it == "localhost" || it == "127.0.0.1" }
        "$Tags.PEER_PORT.key" Integer
        "$Tags.PEER_HOST_IPV4.key" { it == null || it == "127.0.0.1" } // Optional
        "$Tags.HTTP_METHOD.key" method
        "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
      }
    }
  }
}
