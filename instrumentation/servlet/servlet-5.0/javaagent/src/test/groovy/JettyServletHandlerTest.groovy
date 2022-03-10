/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import jakarta.servlet.Servlet
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.ErrorHandler
import org.eclipse.jetty.servlet.ServletHandler
import spock.lang.IgnoreIf

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION

@IgnoreIf({ !jvm.java11Compatible })
class JettyServletHandlerTest extends AbstractServlet5Test<Object, Object> {

  @Override
  Set<AttributeKey<?>> httpAttributes(ServerEndpoint endpoint) {
    def attributes = super.httpAttributes(endpoint)
    attributes.remove(SemanticAttributes.HTTP_ROUTE)
    attributes
  }

  @Override
  Object startServer(int port) {
    Server server = new Server(port)
    ServletHandler handler = new ServletHandler()
    server.setHandler(handler)
    setupServlets(handler)
    server.addBean(new ErrorHandler() {
      protected void handleErrorPage(HttpServletRequest request, Writer writer, int code, String message) throws IOException {
        Throwable th = (Throwable) request.getAttribute("javax.servlet.error.exception")
        writer.write(th ? th.message : message)
      }
    })
    server.start()
    return server
  }

  @Override
  void addServlet(Object handlerObject, String path, Class<Servlet> servlet) {
    ServletHandler servletHandler = (ServletHandler) handlerObject
    servletHandler.addServletWithMapping(servlet, path)
  }

  @Override
  void stopServer(Object serverObject) {
    Server server = (Server) serverObject
    server.stop()
    server.destroy()
  }

  @Override
  String getContextPath() {
    ""
  }

  @Override
  Class<Servlet> servlet() {
    TestServlet5.Sync
  }

  @Override
  boolean testNotFound() {
    false
  }

  @Override
  Throwable expectedException() {
    new ServletException(EXCEPTION.body)
  }
}
