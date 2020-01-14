import io.opentelemetry.auto.api.MoreTags
import io.opentelemetry.auto.api.SpanTypes
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.test.asserts.TraceAssert
import io.opentelemetry.sdk.trace.SpanData
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.ErrorHandler
import org.eclipse.jetty.servlet.ServletContextHandler

import javax.servlet.Servlet
import javax.servlet.http.HttpServletRequest

import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.AUTH_REQUIRED
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.ERROR
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.QUERY_PARAM
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.SUCCESS

abstract class JettyServlet3Test extends AbstractServlet3Test<Server, ServletContextHandler> {

  @Override
  boolean testNotFound() {
    false
  }

  @Override
  Server startServer(int port) {
    def jettyServer = new Server(port)
    jettyServer.connectors.each {
      if (it.hasProperty("resolveNames")) {
        it.resolveNames = true  // get localhost instead of 127.0.0.1
      }
    }

    ServletContextHandler servletContext = new ServletContextHandler(null, "/$context")
    servletContext.errorHandler = new ErrorHandler() {
      protected void handleErrorPage(HttpServletRequest request, Writer writer, int code, String message) throws IOException {
        Throwable th = (Throwable) request.getAttribute("javax.servlet.error.exception")
        writer.write(th ? th.message : message)
      }
    }
//    setupAuthentication(jettyServer, servletContext)
    setupServlets(servletContext)
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
  String getContext() {
    return "jetty-context"
  }

  @Override
  void addServlet(ServletContextHandler servletContext, String path, Class<Servlet> servlet) {
    servletContext.addServlet(servlet, path)
  }

  // FIXME: Add authentication tests back in...
//  static setupAuthentication(Server jettyServer, ServletContextHandler servletContext) {
//    ConstraintSecurityHandler authConfig = new ConstraintSecurityHandler()
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
//    authConfig.setConstraintMappings(mapping)
//    authConfig.setAuthenticator(new BasicAuthenticator())
//
//    LoginService loginService = new HashLoginService("TestRealm",
//      "src/test/resources/realm.properties")
//    authConfig.setLoginService(loginService)
//    jettyServer.addBean(loginService)
//
//    servletContext.setSecurityHandler(authConfig)
//  }
}

class JettyServlet3TestSync extends JettyServlet3Test {

  @Override
  Class<Servlet> servlet() {
    TestServlet3.Sync
  }
}

class JettyServlet3TestAsync extends JettyServlet3Test {

  @Override
  Class<Servlet> servlet() {
    TestServlet3.Async
  }
}

class JettyServlet3TestFakeAsync extends JettyServlet3Test {

  @Override
  Class<Servlet> servlet() {
    TestServlet3.FakeAsync
  }
}

// FIXME: not working right now...
//class JettyServlet3TestForward extends JettyDispatchTest {
//  @Override
//  Class<Servlet> servlet() {
//    TestServlet3.Sync // dispatch to sync servlet
//  }
//
//  @Override
//  boolean testNotFound() {
//    false
//  }
//
//  @Override
//  protected void setupServlets(ServletContextHandler context) {
//    super.setupServlets(context)
//
//    addServlet(context, "/dispatch" + SUCCESS.path, RequestDispatcherServlet.Forward)
//    addServlet(context, "/dispatch" + QUERY_PARAM.path, RequestDispatcherServlet.Forward)
//    addServlet(context, "/dispatch" + REDIRECT.path, RequestDispatcherServlet.Forward)
//    addServlet(context, "/dispatch" + ERROR.path, RequestDispatcherServlet.Forward)
//    addServlet(context, "/dispatch" + EXCEPTION.path, RequestDispatcherServlet.Forward)
//    addServlet(context, "/dispatch" + AUTH_REQUIRED.path, RequestDispatcherServlet.Forward)
//  }
//}

// FIXME: not working right now...
//class JettyServlet3TestInclude extends JettyDispatchTest {
//  @Override
//  Class<Servlet> servlet() {
//    TestServlet3.Sync // dispatch to sync servlet
//  }
//
//  @Override
//  boolean testNotFound() {
//    false
//  }
//
//  @Override
//  protected void setupServlets(ServletContextHandler context) {
//    super.setupServlets(context)
//
//    addServlet(context, "/dispatch" + SUCCESS.path, RequestDispatcherServlet.Include)
//    addServlet(context, "/dispatch" + QUERY_PARAM.path, RequestDispatcherServlet.Include)
//    addServlet(context, "/dispatch" + REDIRECT.path, RequestDispatcherServlet.Include)
//    addServlet(context, "/dispatch" + ERROR.path, RequestDispatcherServlet.Include)
//    addServlet(context, "/dispatch" + EXCEPTION.path, RequestDispatcherServlet.Include)
//    addServlet(context, "/dispatch" + AUTH_REQUIRED.path, RequestDispatcherServlet.Include)
//  }
//}


class JettyServlet3TestDispatchImmediate extends JettyDispatchTest {
  @Override
  Class<Servlet> servlet() {
    TestServlet3.Sync
  }

  @Override
  protected void setupServlets(ServletContextHandler context) {
    super.setupServlets(context)

    addServlet(context, "/dispatch" + SUCCESS.path, TestServlet3.DispatchImmediate)
    addServlet(context, "/dispatch" + QUERY_PARAM.path, TestServlet3.DispatchImmediate)
    addServlet(context, "/dispatch" + ERROR.path, TestServlet3.DispatchImmediate)
    addServlet(context, "/dispatch" + EXCEPTION.path, TestServlet3.DispatchImmediate)
    addServlet(context, "/dispatch" + REDIRECT.path, TestServlet3.DispatchImmediate)
    addServlet(context, "/dispatch" + AUTH_REQUIRED.path, TestServlet3.DispatchImmediate)
    addServlet(context, "/dispatch/recursive", TestServlet3.DispatchRecursive)
  }
}

class JettyServlet3TestDispatchAsync extends JettyDispatchTest {
  @Override
  Class<Servlet> servlet() {
    TestServlet3.Async
  }

  @Override
  protected void setupServlets(ServletContextHandler context) {
    super.setupServlets(context)

    addServlet(context, "/dispatch" + SUCCESS.path, TestServlet3.DispatchAsync)
    addServlet(context, "/dispatch" + QUERY_PARAM.path, TestServlet3.DispatchAsync)
    addServlet(context, "/dispatch" + ERROR.path, TestServlet3.DispatchAsync)
    addServlet(context, "/dispatch" + EXCEPTION.path, TestServlet3.DispatchAsync)
    addServlet(context, "/dispatch" + REDIRECT.path, TestServlet3.DispatchAsync)
    addServlet(context, "/dispatch" + AUTH_REQUIRED.path, TestServlet3.DispatchAsync)
    addServlet(context, "/dispatch/recursive", TestServlet3.DispatchRecursive)
  }
}

abstract class JettyDispatchTest extends JettyServlet3Test {
  @Override
  URI buildAddress() {
    return new URI("http://localhost:$port/$context/dispatch/")
  }

  boolean hasDispatchSpan(ServerEndpoint endpoint) {
    true
  }

  @Override
  void dispatchSpan(TraceAssert trace, int index, Object parent, String method = "GET", ServerEndpoint endpoint = SUCCESS) {
    trace.span(index) {
      operationName expectedOperationName()
      childOf((SpanData) parent)
      errored endpoint.errored
      tags {
        "$MoreTags.SPAN_TYPE" SpanTypes.HTTP_SERVER
        "$Tags.COMPONENT" serverDecorator.component()
        "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
        "$Tags.PEER_HOSTNAME" { it == "localhost" || it == "127.0.0.1" }
        "$Tags.PEER_HOST_IPV4" { it == null || it == "127.0.0.1" } // Optional
        "$Tags.PEER_PORT" Long
        "$Tags.HTTP_URL" "${endpoint.resolve(address).toString().replace("/dispatch", "")}"
        "$Tags.HTTP_METHOD" "GET"
        "$Tags.HTTP_STATUS" endpoint.status
        "servlet.context" "/$context"
        "servlet.path" endpoint.path
        "span.origin.type" {
          it == TestServlet3.DispatchImmediate.name || it == TestServlet3.DispatchAsync.name || it == TestServlet3.Sync.name || it == TestServlet3.Async.name
        }
        if (endpoint.errored) {
          "error.msg" { it == null || it == EXCEPTION.body }
          "error.type" { it == null || it == Exception.name }
          "error.stack" { it == null || it instanceof String }
        }
        if (endpoint.query) {
          "$MoreTags.HTTP_QUERY" endpoint.query
        }
      }
    }
  }
}
