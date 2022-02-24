/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.instrumentation.test.base.HttpServerTest
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.ErrorHandler
import org.eclipse.jetty.servlet.ServletContextHandler

import javax.servlet.http.HttpServletRequest

import static io.opentelemetry.api.trace.SpanKind.INTERNAL
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.AUTH_REQUIRED
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.ERROR
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.INDEXED_CHILD
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.NOT_FOUND
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.PATH_PARAM
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.QUERY_PARAM
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.SUCCESS

class JettyServlet2Test extends HttpServerTest<Server> implements AgentTestTrait {

  private static final CONTEXT = "ctx"

  @Override
  Server startServer(int port) {
    def jettyServer = new Server(port)
    jettyServer.connectors.each {
      it.setHost('localhost')
    }
    ServletContextHandler servletContext = new ServletContextHandler(null, "/$CONTEXT")
    servletContext.errorHandler = new ErrorHandler() {
      protected void handleErrorPage(HttpServletRequest request, Writer writer, int code, String message) throws IOException {
        Throwable th = (Throwable) request.getAttribute("javax.servlet.error.exception")
        writer.write(th ? th.message : message)
      }
    }

    // FIXME: Add tests for security/authentication.
//    ConstraintSecurityHandler security = setupAuthentication(jettyServer)
//    servletContext.setSecurityHandler(security)

    servletContext.addServlet(TestServlet2.Sync, SUCCESS.path)
    servletContext.addServlet(TestServlet2.Sync, QUERY_PARAM.path)
    servletContext.addServlet(TestServlet2.Sync, REDIRECT.path)
    servletContext.addServlet(TestServlet2.Sync, ERROR.path)
    servletContext.addServlet(TestServlet2.Sync, EXCEPTION.path)
    servletContext.addServlet(TestServlet2.Sync, AUTH_REQUIRED.path)
    servletContext.addServlet(TestServlet2.Sync, INDEXED_CHILD.path)

    jettyServer.setHandler(servletContext)
    jettyServer.start()

    return jettyServer
  }

  @Override
  void stopServer(Server server) {
    server.stop()
    server.destroy()
  }

  @Override
  URI buildAddress() {
    return new URI("http://localhost:$port/$CONTEXT/")
  }

  @Override
  Set<AttributeKey<?>> httpAttributes(ServerEndpoint endpoint) {
    [
      SemanticAttributes.NET_TRANSPORT
    ] as Set
  }

  @Override
  String expectedServerSpanName(ServerEndpoint endpoint, String method) {
    switch (endpoint) {
      case NOT_FOUND:
        return "HTTP $method"
      case PATH_PARAM:
        return getContextPath() + "/path/:id/param"
      default:
        return endpoint.resolvePath(address).path
    }
  }

  @Override
  boolean testNotFound() {
    false
  }

  // servlet 2 does not expose a way to retrieve response headers
  @Override
  boolean testCapturedHttpHeaders() {
    false
  }

  @Override
  boolean hasResponseSpan(ServerEndpoint endpoint) {
    endpoint == REDIRECT || endpoint == ERROR
  }

  @Override
  void responseSpan(TraceAssert trace, int index, Object parent, String method = "GET", ServerEndpoint endpoint = SUCCESS) {
    trace.span(index) {
      name endpoint == REDIRECT ? "Response.sendRedirect" : "Response.sendError"
      kind INTERNAL
      childOf((SpanData) parent)
      attributes {
      }
    }
  }

  /**
   * Setup simple authentication for tests
   * <p>
   *     requests to {@code /auth/*} need login 'user' and password 'password'
   * <p>
   *     For details @see <a href="http://www.eclipse.org/jetty/documentation/9.3.x/embedded-examples.html">http://www.eclipse.org/jetty/documentation/9.3.x/embedded-examples.html</a>
   *
   * @param jettyServer server to attach login service
   * @return SecurityHandler that can be assigned to servlet
   */
//  private ConstraintSecurityHandler setupAuthentication(Server jettyServer) {
//    ConstraintSecurityHandler security = new ConstraintSecurityHandler()
//
//    Constraint constraint = new Constraint()
//    constraint.setName("auth")
//    constraint.setAuthenticate(true)
//    constraint.setRoles("role")
//
//    ConstraintMapping mapping = new ConstraintMapping()
//    mapping.setPathSpec("/auth/*")
//    mapping.setConstraint(constraint)
//
//    security.setConstraintMappings(mapping)
//    security.setAuthenticator(new BasicAuthenticator())
//
//    LoginService loginService = new HashLoginService("TestRealm",
//      "src/test/resources/realm.properties")
//    security.setLoginService(loginService)
//    jettyServer.addBean(loginService)
//
//    security
//  }
}
