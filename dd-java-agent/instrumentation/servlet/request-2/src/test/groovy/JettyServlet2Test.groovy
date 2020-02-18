import datadog.trace.agent.test.asserts.TraceAssert
import datadog.trace.agent.test.base.HttpServerTest
import datadog.trace.api.DDSpanTypes
import datadog.trace.api.DDTags
import datadog.trace.bootstrap.instrumentation.api.Tags
import datadog.trace.instrumentation.servlet2.Servlet2Decorator
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.ErrorHandler
import org.eclipse.jetty.servlet.ServletContextHandler

import javax.servlet.http.HttpServletRequest

import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.AUTH_REQUIRED
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.ERROR
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.QUERY_PARAM
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.SUCCESS

class JettyServlet2Test extends HttpServerTest<Server> {

  private static final CONTEXT = "ctx"

  @Override
  Server startServer(int port) {
    def jettyServer = new Server(port)
    jettyServer.connectors.each { it.resolveNames = true } // get localhost instead of 127.0.0.1
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
  String component() {
    return Servlet2Decorator.DECORATE.component()
  }

  @Override
  String expectedServiceName() {
    CONTEXT
  }

  @Override
  String expectedOperationName() {
    return "servlet.request"
  }

  @Override
  boolean testNotFound() {
    false
  }

  // parent span must be cast otherwise it breaks debugging classloading (junit loads it early)
  void serverSpan(TraceAssert trace, int index, BigInteger traceID = null, BigInteger parentID = null, String method = "GET", ServerEndpoint endpoint = SUCCESS) {
    trace.span(index) {
      serviceName expectedServiceName()
      operationName expectedOperationName()
      resourceName endpoint.status == 404 ? "404" : "$method ${endpoint.resolve(address).path}"
      spanType DDSpanTypes.HTTP_SERVER
      errored endpoint.errored
      if (parentID != null) {
        traceId traceID
        parentId parentID
      } else {
        parent()
      }
      tags {
        "$Tags.COMPONENT" component
        "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
        "$Tags.PEER_HOST_IPV4" "127.0.0.1"
        // No peer port
        "$Tags.HTTP_URL" "${endpoint.resolve(address)}"
        "$Tags.HTTP_METHOD" method
        "$Tags.HTTP_STATUS" endpoint.status
        "servlet.context" "/$CONTEXT"
        "servlet.path" endpoint.path
        "span.origin.type" TestServlet2.Sync.name
        if (endpoint.errored) {
          "error.msg" { it == null || it == EXCEPTION.body }
          "error.type" { it == null || it == Exception.name }
          "error.stack" { it == null || it instanceof String }
        }
        if (endpoint.query) {
          "$DDTags.HTTP_QUERY" endpoint.query
        }
        defaultTags(true)
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
