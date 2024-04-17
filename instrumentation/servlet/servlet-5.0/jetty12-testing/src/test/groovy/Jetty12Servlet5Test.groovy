/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.javaagent.instrumentation.servlet.v5_0.RequestDispatcherServlet
import jakarta.servlet.Servlet
import jakarta.servlet.ServletException
import org.eclipse.jetty.ee10.servlet.ServletContextHandler
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Response
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.util.Callback
import test.AbstractServlet5Test
import test.TestServlet5

import java.nio.charset.StandardCharsets

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.AUTH_REQUIRED
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_HEADERS
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_PARAMETERS
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.QUERY_PARAM
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS

abstract class Jetty12Servlet5Test extends AbstractServlet5Test<Object, Object> {

  @Override
  boolean testNotFound() {
    false
  }

  @Override
  Throwable expectedException() {
    new ServletException(EXCEPTION.body)
  }

  @Override
  Object startServer(int port) {
    def jettyServer = new Server(port)
    jettyServer.connectors.each {
      it.setHost('localhost')
    }

    ServletContextHandler servletContext = new ServletContextHandler(contextPath)
    servletContext.errorHandler = new Request.Handler() {

      @Override
      boolean handle(Request request, Response response, Callback callback) throws Exception {
        String message = (String) request.getAttribute("org.eclipse.jetty.server.error_message")
        if (message != null) {
          response.write(true, StandardCharsets.UTF_8.encode(message), Callback.NOOP)
        }
        callback.succeeded()
        return true
      }
    }

    setupServlets(servletContext)
    jettyServer.setHandler(servletContext)

    jettyServer.start()

    return jettyServer
  }

  @Override
  void stopServer(Object serverObject) {
    Server server = (Server) serverObject
    server.stop()
    server.destroy()
  }

  @Override
  String getContextPath() {
    return "/jetty-context"
  }

  @Override
  void addServlet(Object handlerObject, String path, Class<Servlet> servlet) {
    ServletContextHandler handler = (ServletContextHandler) handlerObject
    handler.addServlet(servlet, path)
  }
}

class JettyServlet5TestSync extends Jetty12Servlet5Test {

  @Override
  Class<Servlet> servlet() {
    TestServlet5.Sync
  }
}

class JettyServlet5TestAsync extends Jetty12Servlet5Test {

  @Override
  Class<Servlet> servlet() {
    TestServlet5.Async
  }

  @Override
  boolean errorEndpointUsesSendError() {
    false
  }
}

class JettyServlet5TestFakeAsync extends Jetty12Servlet5Test {

  @Override
  Class<Servlet> servlet() {
    TestServlet5.FakeAsync
  }
}

class JettyServlet5TestForward extends JettyDispatchTest {
  @Override
  Class<Servlet> servlet() {
    TestServlet5.Sync // dispatch to sync servlet
  }

  @Override
  protected void setupServlets(Object context) {
    super.setupServlets(context)

    addServlet(context, "/dispatch" + SUCCESS.path, RequestDispatcherServlet.Forward)
    addServlet(context, "/dispatch" + HTML_PRINT_WRITER.path, RequestDispatcherServlet.Forward)
    addServlet(context, "/dispatch" + HTML_SERVLET_OUTPUT_STREAM.path, RequestDispatcherServlet.Forward)
    addServlet(context, "/dispatch" + QUERY_PARAM.path, RequestDispatcherServlet.Forward)
    addServlet(context, "/dispatch" + REDIRECT.path, RequestDispatcherServlet.Forward)
    addServlet(context, "/dispatch" + ERROR.path, RequestDispatcherServlet.Forward)
    addServlet(context, "/dispatch" + EXCEPTION.path, RequestDispatcherServlet.Forward)
    addServlet(context, "/dispatch" + AUTH_REQUIRED.path, RequestDispatcherServlet.Forward)
    addServlet(context, "/dispatch" + CAPTURE_HEADERS.path, RequestDispatcherServlet.Forward)
    addServlet(context, "/dispatch" + CAPTURE_PARAMETERS.path, RequestDispatcherServlet.Forward)
    addServlet(context, "/dispatch" + INDEXED_CHILD.path, RequestDispatcherServlet.Forward)
  }
}

class JettyServlet5TestInclude extends JettyDispatchTest {
  @Override
  Class<Servlet> servlet() {
    TestServlet5.Sync // dispatch to sync servlet
  }

  @Override
  boolean testRedirect() {
    false
  }

  @Override
  boolean testCapturedHttpHeaders() {
    false
  }

  @Override
  boolean testError() {
    false
  }

  @Override
  protected void setupServlets(Object context) {
    super.setupServlets(context)

    addServlet(context, "/dispatch" + SUCCESS.path, RequestDispatcherServlet.Include)
    addServlet(context, "/dispatch" + HTML_PRINT_WRITER.path, RequestDispatcherServlet.Include)
    addServlet(context, "/dispatch" + HTML_SERVLET_OUTPUT_STREAM.path, RequestDispatcherServlet.Include)
    addServlet(context, "/dispatch" + QUERY_PARAM.path, RequestDispatcherServlet.Include)
    addServlet(context, "/dispatch" + REDIRECT.path, RequestDispatcherServlet.Include)
    addServlet(context, "/dispatch" + ERROR.path, RequestDispatcherServlet.Include)
    addServlet(context, "/dispatch" + EXCEPTION.path, RequestDispatcherServlet.Include)
    addServlet(context, "/dispatch" + AUTH_REQUIRED.path, RequestDispatcherServlet.Include)
    addServlet(context, "/dispatch" + CAPTURE_HEADERS.path, RequestDispatcherServlet.Include)
    addServlet(context, "/dispatch" + CAPTURE_PARAMETERS.path, RequestDispatcherServlet.Include)
    addServlet(context, "/dispatch" + INDEXED_CHILD.path, RequestDispatcherServlet.Include)
  }
}


class JettyServlet5TestDispatchImmediate extends JettyDispatchTest {
  @Override
  Class<Servlet> servlet() {
    TestServlet5.Sync
  }

  @Override
  protected void setupServlets(Object context) {
    super.setupServlets(context)
    addServlet(context, "/dispatch" + HTML_PRINT_WRITER.path, TestServlet5.DispatchImmediate)
    addServlet(context, "/dispatch" + HTML_SERVLET_OUTPUT_STREAM.path, TestServlet5.DispatchImmediate)
    addServlet(context, "/dispatch" + SUCCESS.path, TestServlet5.DispatchImmediate)
    addServlet(context, "/dispatch" + QUERY_PARAM.path, TestServlet5.DispatchImmediate)
    addServlet(context, "/dispatch" + ERROR.path, TestServlet5.DispatchImmediate)
    addServlet(context, "/dispatch" + EXCEPTION.path, TestServlet5.DispatchImmediate)
    addServlet(context, "/dispatch" + REDIRECT.path, TestServlet5.DispatchImmediate)
    addServlet(context, "/dispatch" + AUTH_REQUIRED.path, TestServlet5.DispatchImmediate)
    addServlet(context, "/dispatch" + CAPTURE_HEADERS.path, TestServlet5.DispatchImmediate)
    addServlet(context, "/dispatch" + CAPTURE_PARAMETERS.path, TestServlet5.DispatchImmediate)
    addServlet(context, "/dispatch" + INDEXED_CHILD.path, TestServlet5.DispatchImmediate)
    addServlet(context, "/dispatch/recursive", TestServlet5.DispatchRecursive)
  }
}

class JettyServlet5TestDispatchAsync extends JettyDispatchTest {
  @Override
  Class<Servlet> servlet() {
    TestServlet5.Async
  }

  @Override
  protected void setupServlets(Object context) {
    super.setupServlets(context)

    addServlet(context, "/dispatch" + SUCCESS.path, TestServlet5.DispatchAsync)
    addServlet(context, "/dispatch" + HTML_PRINT_WRITER.path, TestServlet5.DispatchAsync)
    addServlet(context, "/dispatch" + HTML_SERVLET_OUTPUT_STREAM.path, TestServlet5.DispatchAsync)
    addServlet(context, "/dispatch" + QUERY_PARAM.path, TestServlet5.DispatchAsync)
    addServlet(context, "/dispatch" + ERROR.path, TestServlet5.DispatchAsync)
    addServlet(context, "/dispatch" + EXCEPTION.path, TestServlet5.DispatchAsync)
    addServlet(context, "/dispatch" + REDIRECT.path, TestServlet5.DispatchAsync)
    addServlet(context, "/dispatch" + AUTH_REQUIRED.path, TestServlet5.DispatchAsync)
    addServlet(context, "/dispatch" + CAPTURE_HEADERS.path, TestServlet5.DispatchAsync)
    addServlet(context, "/dispatch" + CAPTURE_PARAMETERS.path, TestServlet5.DispatchAsync)
    addServlet(context, "/dispatch" + INDEXED_CHILD.path, TestServlet5.DispatchAsync)
    addServlet(context, "/dispatch/recursive", TestServlet5.DispatchRecursive)
  }

  @Override
  boolean errorEndpointUsesSendError() {
    false
  }
}

abstract class JettyDispatchTest extends Jetty12Servlet5Test {
  @Override
  URI buildAddress() {
    return new URI("http://localhost:$port$contextPath/dispatch/")
  }
}
