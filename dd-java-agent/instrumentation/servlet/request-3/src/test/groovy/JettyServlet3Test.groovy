import datadog.opentracing.DDSpan
import datadog.trace.agent.test.asserts.ListWriterAssert
import datadog.trace.api.DDSpanTypes
import datadog.trace.api.DDTags
import datadog.trace.bootstrap.instrumentation.api.Tags
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.ErrorHandler
import org.eclipse.jetty.servlet.ServletContextHandler

import javax.servlet.Servlet
import javax.servlet.http.HttpServletRequest

import static datadog.trace.agent.test.asserts.TraceAssert.assertTrace
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.AUTH_REQUIRED
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.ERROR
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.QUERY_PARAM
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.SUCCESS
import static datadog.trace.agent.test.utils.TraceUtils.basicSpan

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

  @Override
  void cleanAndAssertTraces(
    final int size,
    @ClosureParams(value = SimpleType, options = "datadog.trace.agent.test.asserts.ListWriterAssert")
    @DelegatesTo(value = ListWriterAssert, strategy = Closure.DELEGATE_FIRST)
    final Closure spec) {

    // If this is failing, make sure HttpServerTestAdvice is applied correctly.
    TEST_WRITER.waitForTraces(size * 3) // (test, dispatch, and servlet/controller traces
    // TEST_WRITER is a CopyOnWriteArrayList, which doesn't support remove()
    def toRemove = TEST_WRITER.findAll {
      it.size() == 1 && it.get(0).operationName == "TEST_SPAN"
    }
    assert toRemove.size() == size
    toRemove.each {
      assertTrace(it, 1) {
        basicSpan(it, 0, "TEST_SPAN", "ServerEntry")
      }
    }
    TEST_WRITER.removeAll(toRemove)

    // Validate dispatch trace
    def dispatchTraces = TEST_WRITER.findAll {
      it.size() == 1 && it.get(0).resourceName.contains("/dispatch/")
    }
    assert dispatchTraces.size() == size
    dispatchTraces.each { List<DDSpan> dispatchTrace ->
      assertTrace(dispatchTrace, 1) {
        def endpoint = lastRequest
        span(0) {
          serviceName expectedServiceName()
          operationName expectedOperationName()
          resourceName endpoint.status == 404 ? "404" : "GET ${endpoint.resolve(address).path}"
          spanType DDSpanTypes.HTTP_SERVER
          errored endpoint.errored
          // we can't reliably assert parent or child relationship here since both are tested.
          tags {
            "$Tags.COMPONENT" component
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
            "$Tags.PEER_HOST_IPV4" { it == null || it == "127.0.0.1" } // Optional
            "$Tags.PEER_PORT" Integer
            "$Tags.HTTP_URL" "${endpoint.resolve(address)}"
            "$Tags.HTTP_METHOD" "GET"
            "$Tags.HTTP_STATUS" endpoint.status
            "servlet.context" "/$context"
            "servlet.path" endpoint.status == 404 ? endpoint.path : "/dispatch$endpoint.path"
            "servlet.dispatch" endpoint.path
            "span.origin.type" {
              it == TestServlet3.DispatchImmediate.name || it == TestServlet3.DispatchAsync.name || it == ApplicationFilterChain.name
            }
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
      // Make sure that the trace has a span with the dispatchTrace as a parent.
      assert TEST_WRITER.any { it.any { it.parentId == dispatchTrace[0].spanId } }
    }
  }
}
