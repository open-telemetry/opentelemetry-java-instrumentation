import com.google.common.io.Files
import datadog.opentracing.DDSpan
import datadog.trace.agent.test.asserts.ListWriterAssert
import datadog.trace.api.CorrelationIdentifier
import datadog.trace.api.DDSpanTypes
import datadog.trace.api.DDTags
import datadog.trace.bootstrap.instrumentation.api.Tags
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import org.apache.catalina.AccessLog
import org.apache.catalina.Context
import org.apache.catalina.connector.Request
import org.apache.catalina.connector.Response
import org.apache.catalina.core.ApplicationFilterChain
import org.apache.catalina.core.StandardHost
import org.apache.catalina.startup.Tomcat
import org.apache.catalina.valves.ErrorReportValve
import org.apache.catalina.valves.ValveBase
import org.apache.tomcat.JarScanFilter
import org.apache.tomcat.JarScanType
import spock.lang.Shared
import spock.lang.Unroll

import javax.servlet.Servlet
import javax.servlet.ServletException

import static datadog.trace.agent.test.asserts.TraceAssert.assertTrace
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.AUTH_REQUIRED
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.ERROR
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.NOT_FOUND
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.QUERY_PARAM
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.SUCCESS
import static datadog.trace.agent.test.utils.TraceUtils.basicSpan

@Unroll
abstract class TomcatServlet3Test extends AbstractServlet3Test<Tomcat, Context> {

  @Shared
  def accessLogValue = new TestAccessLogValve()

  @Override
  Tomcat startServer(int port) {
    def tomcatServer = new Tomcat()

    def baseDir = Files.createTempDir()
    baseDir.deleteOnExit()
    tomcatServer.setBaseDir(baseDir.getAbsolutePath())

    tomcatServer.setPort(port)
    tomcatServer.getConnector().enableLookups = true // get localhost instead of 127.0.0.1

    final File applicationDir = new File(baseDir, "/webapps/ROOT")
    if (!applicationDir.exists()) {
      applicationDir.mkdirs()
      applicationDir.deleteOnExit()
    }
    Context servletContext = tomcatServer.addWebapp("/$context", applicationDir.getAbsolutePath())
    // Speed up startup by disabling jar scanning:
    servletContext.getJarScanner().setJarScanFilter(new JarScanFilter() {
      @Override
      boolean check(JarScanType jarScanType, String jarName) {
        return false
      }
    })

//    setupAuthentication(tomcatServer, servletContext)
    setupServlets(servletContext)

    (tomcatServer.host as StandardHost).errorReportValveClass = ErrorHandlerValve.name
    (tomcatServer.host as StandardHost).getPipeline().addValve(accessLogValue)

    tomcatServer.start()

    return tomcatServer
  }

  def setup() {
    accessLogValue.loggedIds.clear()
  }

  @Override
  void stopServer(Tomcat server) {
    server.stop()
    server.destroy()
  }

  @Override
  String getContext() {
    return "tomcat-context"
  }

  @Override
  void addServlet(Context servletContext, String path, Class<Servlet> servlet) {
    String name = UUID.randomUUID()
    Tomcat.addServlet(servletContext, name, servlet.newInstance())
    servletContext.addServletMappingDecoded(path, name)
  }


  def "access log has ids for #count requests"() {
    given:
    def request = request(SUCCESS, method, body).build()

    when:
    List<okhttp3.Response> responses = (1..count).collect {
      return client.newCall(request).execute()
    }

    then:
    responses.each { response ->
      assert response.code() == SUCCESS.status
      assert response.body().string() == SUCCESS.body
    }

    and:
    cleanAndAssertTraces(count) {
      (1..count).eachWithIndex { val, i ->
        trace(i, 2) {
          serverSpan(it, 0)
          controllerSpan(it, 1, span(0))
        }

        def (String traceId, String spanId) = accessLogValue.loggedIds[i]
        assert trace(i).get(0).traceId.toString() == traceId
        assert trace(i).get(0).spanId.toString() == spanId
      }
    }

    where:
    method = "GET"
    body = null
    count << [1, 4] // make multiple requests.
  }

  def "access log has ids for error request"() {
    setup:
    def request = request(ERROR, method, body).build()
    def response = client.newCall(request).execute()

    expect:
    response.code() == ERROR.status
    response.body().string() == ERROR.body

    and:
    cleanAndAssertTraces(1) {
      trace(0, 2) {
        serverSpan(it, 0, null, null, method, ERROR)
        controllerSpan(it, 1, span(0))
      }

      def (String traceId, String spanId) = accessLogValue.loggedIds[0]
      assert trace(0).get(0).traceId.toString() == traceId
      assert trace(0).get(0).spanId.toString() == spanId
    }

    where:
    method = "GET"
    body = null
  }

  // FIXME: Add authentication tests back in...
//  private setupAuthentication(Tomcat server, Context servletContext) {
//    // Login Config
//    LoginConfig authConfig = new LoginConfig()
//    authConfig.setAuthMethod("BASIC")
//
//    // adding constraint with role "test"
//    SecurityConstraint constraint = new SecurityConstraint()
//    constraint.addAuthRole("role")
//
//    // add constraint to a collection with pattern /second
//    SecurityCollection collection = new SecurityCollection()
//    collection.addPattern("/auth/*")
//    constraint.addCollection(collection)
//
//    servletContext.setLoginConfig(authConfig)
//    // does the context need a auth role too?
//    servletContext.addSecurityRole("role")
//    servletContext.addConstraint(constraint)
//
//    // add tomcat users to realm
//    MemoryRealm realm = new MemoryRealm() {
//      protected void startInternal() {
//        credentialHandler = new MessageDigestCredentialHandler()
//        setState(LifecycleState.STARTING)
//      }
//    }
//    realm.addUser(user, pass, "role")
//    server.getEngine().setRealm(realm)
//
//    servletContext.setLoginConfig(authConfig)
//  }
}

class ErrorHandlerValve extends ErrorReportValve {
  @Override
  protected void report(Request request, Response response, Throwable t) {
    if (response.getStatus() < 400 || response.getContentWritten() > 0 || !response.setErrorReported()) {
      return
    }
    try {
      response.writer.print(t ? t.cause.message : response.message)
    } catch (IOException e) {
      e.printStackTrace()
    }
  }
}

class TestAccessLogValve extends ValveBase implements AccessLog {
  List<Tuple2<String, String>> loggedIds = []

  TestAccessLogValve() {
    super(true)
  }

  void log(Request request, Response response, long time) {
    loggedIds.add(new Tuple2(request.getAttribute(CorrelationIdentifier.traceIdKey),
      request.getAttribute(CorrelationIdentifier.spanIdKey)))
  }

  @Override
  void setRequestAttributesEnabled(boolean requestAttributesEnabled) {
  }

  @Override
  boolean getRequestAttributesEnabled() {
    return false
  }

  @Override
  void invoke(Request request, Response response) throws IOException, ServletException {
    getNext().invoke(request, response)
  }
}

class TomcatServlet3TestSync extends TomcatServlet3Test {

  @Override
  Class<Servlet> servlet() {
    TestServlet3.Sync
  }
}

class TomcatServlet3TestAsync extends TomcatServlet3Test {

  @Override
  Class<Servlet> servlet() {
    TestServlet3.Async
  }
}

class TomcatServlet3TestFakeAsync extends TomcatServlet3Test {

  @Override
  Class<Servlet> servlet() {
    TestServlet3.FakeAsync
  }
}

// FIXME: not working right now...
//class TomcatServlet3TestForward extends TomcatDispatchTest {
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
//  protected void setupServlets(Context context) {
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
//class TomcatServlet3TestInclude extends TomcatDispatchTest {
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
//  protected void setupServlets(Context context) {
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

class TomcatServlet3TestDispatchImmediate extends TomcatDispatchTest {
  @Override
  Class<Servlet> servlet() {
    TestServlet3.Sync
  }

  @Override
  boolean testNotFound() {
    false
  }

  @Override
  protected void setupServlets(Context context) {
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

class TomcatServlet3TestDispatchAsync extends TomcatDispatchTest {
  @Override
  Class<Servlet> servlet() {
    TestServlet3.Async
  }

  @Override
  protected void setupServlets(Context context) {
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

abstract class TomcatDispatchTest extends TomcatServlet3Test {
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
    if (lastRequest == NOT_FOUND) {
      TEST_WRITER.waitForTraces(size * 2) // (test and servlet/controller traces
    } else {
      TEST_WRITER.waitForTraces(size * 3) // (test, dispatch, and servlet/controller traces
    }
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

    if (lastRequest == NOT_FOUND) {
      // Tomcat won't "dispatch" an unregistered url
      assertTraces(size, spec)
      return
    }

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
