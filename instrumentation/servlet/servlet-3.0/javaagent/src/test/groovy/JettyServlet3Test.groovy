/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.ErrorHandler
import org.eclipse.jetty.servlet.ServletContextHandler

import javax.servlet.Servlet
import javax.servlet.ServletException
import javax.servlet.http.HttpServletRequest

import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.AUTH_REQUIRED
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.CAPTURE_HEADERS
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.ERROR
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.INDEXED_CHILD
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.QUERY_PARAM
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.SUCCESS

abstract class JettyServlet3Test extends AbstractServlet3Test<Server, ServletContextHandler> {

  private static final boolean IS_BEFORE_94 = isBefore94()

  static isBefore94() {
    def version = Server.getVersion().split("\\.")
    def major = Integer.parseInt(version[0])
    def minor = Integer.parseInt(version[1])
    return major < 9 || (major == 9 && minor < 4)
  }

  @Override
  boolean testNotFound() {
    false
  }

  @Override
  Class<?> expectedExceptionClass() {
    ServletException
  }

  boolean isAsyncTest() {
    false
  }

  @Override
  boolean hasResponseSpan(ServerEndpoint endpoint) {
    return (IS_BEFORE_94 && endpoint == EXCEPTION && !isAsyncTest()) || super.hasResponseSpan(endpoint)
  }

  @Override
  void responseSpan(TraceAssert trace, int index, Object controllerSpan, Object handlerSpan, String method, ServerEndpoint endpoint) {
    if (IS_BEFORE_94 && endpoint == EXCEPTION) {
      sendErrorSpan(trace, index, handlerSpan)
    }
    super.responseSpan(trace, index, controllerSpan, handlerSpan, method, endpoint)
  }

  @Override
  Server startServer(int port) {
    def jettyServer = new Server(port)
    jettyServer.connectors.each {
      it.setHost('localhost')
    }

    ServletContextHandler servletContext = new ServletContextHandler(null, contextPath)
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
  String getContextPath() {
    return "/jetty-context"
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

  @Override
  boolean errorEndpointUsesSendError() {
    false
  }

  @Override
  boolean isAsyncTest() {
    true
  }
}

class JettyServlet3TestFakeAsync extends JettyServlet3Test {

  @Override
  Class<Servlet> servlet() {
    TestServlet3.FakeAsync
  }

  @Override
  boolean testException() {
    // we expect this request to fail with http 500 but is succeeds with http 200
    // when using -PtestLatestDeps=true
    false
  }
}

class JettyServlet3TestForward extends JettyDispatchTest {
  @Override
  Class<Servlet> servlet() {
    TestServlet3.Sync // dispatch to sync servlet
  }

  @Override
  protected void setupServlets(ServletContextHandler context) {
    super.setupServlets(context)

    addServlet(context, "/dispatch" + SUCCESS.path, RequestDispatcherServlet.Forward)
    addServlet(context, "/dispatch" + QUERY_PARAM.path, RequestDispatcherServlet.Forward)
    addServlet(context, "/dispatch" + REDIRECT.path, RequestDispatcherServlet.Forward)
    addServlet(context, "/dispatch" + ERROR.path, RequestDispatcherServlet.Forward)
    addServlet(context, "/dispatch" + EXCEPTION.path, RequestDispatcherServlet.Forward)
    addServlet(context, "/dispatch" + AUTH_REQUIRED.path, RequestDispatcherServlet.Forward)
    addServlet(context, "/dispatch" + CAPTURE_HEADERS.path, RequestDispatcherServlet.Forward)
    addServlet(context, "/dispatch" + INDEXED_CHILD.path, RequestDispatcherServlet.Forward)
  }
}

class JettyServlet3TestInclude extends JettyDispatchTest {
  @Override
  Class<Servlet> servlet() {
    TestServlet3.Sync // dispatch to sync servlet
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
  protected void setupServlets(ServletContextHandler context) {
    super.setupServlets(context)

    addServlet(context, "/dispatch" + SUCCESS.path, RequestDispatcherServlet.Include)
    addServlet(context, "/dispatch" + QUERY_PARAM.path, RequestDispatcherServlet.Include)
    addServlet(context, "/dispatch" + REDIRECT.path, RequestDispatcherServlet.Include)
    addServlet(context, "/dispatch" + ERROR.path, RequestDispatcherServlet.Include)
    addServlet(context, "/dispatch" + EXCEPTION.path, RequestDispatcherServlet.Include)
    addServlet(context, "/dispatch" + AUTH_REQUIRED.path, RequestDispatcherServlet.Include)
    addServlet(context, "/dispatch" + INDEXED_CHILD.path, RequestDispatcherServlet.Include)
  }
}


class JettyServlet3TestDispatchImmediate extends JettyDispatchTest {
  @Override
  Class<Servlet> servlet() {
    TestServlet3.Sync
  }

  @Override
  boolean isAsyncTest() {
    true
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
    addServlet(context, "/dispatch" + CAPTURE_HEADERS.path, TestServlet3.DispatchImmediate)
    addServlet(context, "/dispatch" + INDEXED_CHILD.path, TestServlet3.DispatchImmediate)
    addServlet(context, "/dispatch/recursive", TestServlet3.DispatchRecursive)
  }
}

class JettyServlet3TestDispatchAsync extends JettyDispatchTest {
  @Override
  Class<Servlet> servlet() {
    TestServlet3.Async
  }

  @Override
  boolean isAsyncTest() {
    true
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
    addServlet(context, "/dispatch" + CAPTURE_HEADERS.path, TestServlet3.DispatchAsync)
    addServlet(context, "/dispatch" + INDEXED_CHILD.path, TestServlet3.DispatchAsync)
    addServlet(context, "/dispatch/recursive", TestServlet3.DispatchRecursive)
  }

  @Override
  boolean errorEndpointUsesSendError() {
    false
  }
}

abstract class JettyDispatchTest extends JettyServlet3Test {
  @Override
  URI buildAddress() {
    return new URI("http://localhost:$port$contextPath/dispatch/")
  }
}
