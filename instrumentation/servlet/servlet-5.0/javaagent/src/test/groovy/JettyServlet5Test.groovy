/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */


import jakarta.servlet.Servlet
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.ErrorHandler
import org.eclipse.jetty.servlet.ServletContextHandler
import spock.lang.IgnoreIf

import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.AUTH_REQUIRED
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.CAPTURE_HEADERS
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.ERROR
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.QUERY_PARAM
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.SUCCESS

abstract class JettyServlet5Test extends AbstractServlet5Test<Object, Object> {

  @Override
  boolean testNotFound() {
    false
  }

  @Override
  Class<?> expectedExceptionClass() {
    ServletException
  }

  @Override
  Object startServer(int port) {
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

@IgnoreIf({ !jvm.java11Compatible })
class JettyServlet5TestSync extends JettyServlet5Test {

  @Override
  Class<Servlet> servlet() {
    TestServlet5.Sync
  }

  @Override
  boolean testConcurrency() {
    return true
  }
}

@IgnoreIf({ !jvm.java11Compatible })
class JettyServlet5TestAsync extends JettyServlet5Test {

  @Override
  Class<Servlet> servlet() {
    TestServlet5.Async
  }

  @Override
  boolean errorEndpointUsesSendError() {
    false
  }

  @Override
  boolean testException() {
    // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/807
    return false
  }

  @Override
  boolean testConcurrency() {
    return true
  }
}

@IgnoreIf({ !jvm.java11Compatible })
class JettyServlet5TestFakeAsync extends JettyServlet5Test {

  @Override
  Class<Servlet> servlet() {
    TestServlet5.FakeAsync
  }

  @Override
  boolean testException() {
    // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/807
    return false
  }

  @Override
  boolean testConcurrency() {
    return true
  }
}

@IgnoreIf({ !jvm.java11Compatible })
class JettyServlet5TestForward extends JettyDispatchTest {
  @Override
  Class<Servlet> servlet() {
    TestServlet5.Sync // dispatch to sync servlet
  }

  @Override
  protected void setupServlets(Object context) {
    super.setupServlets(context)

    addServlet(context, "/dispatch" + SUCCESS.path, RequestDispatcherServlet.Forward)
    addServlet(context, "/dispatch" + QUERY_PARAM.path, RequestDispatcherServlet.Forward)
    addServlet(context, "/dispatch" + REDIRECT.path, RequestDispatcherServlet.Forward)
    addServlet(context, "/dispatch" + ERROR.path, RequestDispatcherServlet.Forward)
    addServlet(context, "/dispatch" + EXCEPTION.path, RequestDispatcherServlet.Forward)
    addServlet(context, "/dispatch" + AUTH_REQUIRED.path, RequestDispatcherServlet.Forward)
    addServlet(context, "/dispatch" + CAPTURE_HEADERS.path, RequestDispatcherServlet.Forward)
  }
}

@IgnoreIf({ !jvm.java11Compatible })
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
    addServlet(context, "/dispatch" + QUERY_PARAM.path, RequestDispatcherServlet.Include)
    addServlet(context, "/dispatch" + REDIRECT.path, RequestDispatcherServlet.Include)
    addServlet(context, "/dispatch" + ERROR.path, RequestDispatcherServlet.Include)
    addServlet(context, "/dispatch" + EXCEPTION.path, RequestDispatcherServlet.Include)
    addServlet(context, "/dispatch" + AUTH_REQUIRED.path, RequestDispatcherServlet.Include)
  }
}


@IgnoreIf({ !jvm.java11Compatible })
class JettyServlet5TestDispatchImmediate extends JettyDispatchTest {
  @Override
  Class<Servlet> servlet() {
    TestServlet5.Sync
  }

  @Override
  protected void setupServlets(Object context) {
    super.setupServlets(context)

    addServlet(context, "/dispatch" + SUCCESS.path, TestServlet5.DispatchImmediate)
    addServlet(context, "/dispatch" + QUERY_PARAM.path, TestServlet5.DispatchImmediate)
    addServlet(context, "/dispatch" + ERROR.path, TestServlet5.DispatchImmediate)
    addServlet(context, "/dispatch" + EXCEPTION.path, TestServlet5.DispatchImmediate)
    addServlet(context, "/dispatch" + REDIRECT.path, TestServlet5.DispatchImmediate)
    addServlet(context, "/dispatch" + AUTH_REQUIRED.path, TestServlet5.DispatchImmediate)
    addServlet(context, "/dispatch" + CAPTURE_HEADERS.path, TestServlet5.DispatchImmediate)
    addServlet(context, "/dispatch/recursive", TestServlet5.DispatchRecursive)
  }

  @Override
  boolean testException() {
    // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/807
    return false
  }
}

@IgnoreIf({ !jvm.java11Compatible })
class JettyServlet5TestDispatchAsync extends JettyDispatchTest {
  @Override
  Class<Servlet> servlet() {
    TestServlet5.Async
  }

  @Override
  protected void setupServlets(Object context) {
    super.setupServlets(context)

    addServlet(context, "/dispatch" + SUCCESS.path, TestServlet5.DispatchAsync)
    addServlet(context, "/dispatch" + QUERY_PARAM.path, TestServlet5.DispatchAsync)
    addServlet(context, "/dispatch" + ERROR.path, TestServlet5.DispatchAsync)
    addServlet(context, "/dispatch" + EXCEPTION.path, TestServlet5.DispatchAsync)
    addServlet(context, "/dispatch" + REDIRECT.path, TestServlet5.DispatchAsync)
    addServlet(context, "/dispatch" + AUTH_REQUIRED.path, TestServlet5.DispatchAsync)
    addServlet(context, "/dispatch" + CAPTURE_HEADERS.path, TestServlet5.DispatchAsync)
    addServlet(context, "/dispatch/recursive", TestServlet5.DispatchRecursive)
  }

  @Override
  boolean errorEndpointUsesSendError() {
    false
  }

  @Override
  boolean testException() {
    // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/807
    return false
  }
}

abstract class JettyDispatchTest extends JettyServlet5Test {
  @Override
  URI buildAddress() {
    return new URI("http://localhost:$port$contextPath/dispatch/")
  }
}
