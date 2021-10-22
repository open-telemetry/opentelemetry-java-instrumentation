/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse
import org.apache.catalina.AccessLog
import org.apache.catalina.Context
import org.apache.catalina.connector.Request
import org.apache.catalina.connector.Response
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
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.AUTH_REQUIRED
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.CAPTURE_HEADERS
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.ERROR
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.INDEXED_CHILD
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.NOT_FOUND
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.QUERY_PARAM
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.SUCCESS
import static org.junit.jupiter.api.Assumptions.assumeTrue

@Unroll
abstract class TomcatServlet3Test extends AbstractServlet3Test<Tomcat, Context> {

  @Override
  List<AttributeKey<?>> extraAttributes() {
    [
      SemanticAttributes.HTTP_SERVER_NAME,
      SemanticAttributes.NET_PEER_NAME,
      SemanticAttributes.NET_TRANSPORT
    ]
  }


  @Override
  Class<?> expectedExceptionClass() {
    ServletException
  }

  @Override
  boolean hasResponseSpan(ServerEndpoint endpoint) {
    endpoint == NOT_FOUND || super.hasResponseSpan(endpoint)
  }

  @Override
  void responseSpan(TraceAssert trace, int index, Object parent, String method, ServerEndpoint endpoint) {
    switch (endpoint) {
      case NOT_FOUND:
        sendErrorSpan(trace, index, parent)
        break
    }
    super.responseSpan(trace, index, parent, method, endpoint)
  }

  @Shared
  def accessLogValue = new TestAccessLogValve()

  @Override
  Tomcat startServer(int port) {
    def tomcatServer = new Tomcat()

    def baseDir = Files.createTempDirectory("tomcat").toFile()
    baseDir.deleteOnExit()
    tomcatServer.setBaseDir(baseDir.getAbsolutePath())

    tomcatServer.setPort(port)
    tomcatServer.getConnector().enableLookups = true // get localhost instead of 127.0.0.1

    File applicationDir = new File(baseDir, "/webapps/ROOT")
    if (!applicationDir.exists()) {
      applicationDir.mkdirs()
      applicationDir.deleteOnExit()
    }
    Context servletContext = tomcatServer.addWebapp(contextPath, applicationDir.getAbsolutePath())
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
  String getContextPath() {
    return "/tomcat-context"
  }

  @Override
  void addServlet(Context servletContext, String path, Class<Servlet> servlet) {
    String name = UUID.randomUUID()
    Tomcat.addServlet(servletContext, name, servlet.newInstance())
    servletContext.addServletMappingDecoded(path, name)
  }

  def "access log has ids for #count requests"() {
    given:
    def request = request(SUCCESS, method)

    when:
    List<AggregatedHttpResponse> responses = (1..count).collect {
      return client.execute(request).aggregate().join()
    }

    then:
    responses.each { response ->
      assert response.status().code() == SUCCESS.status
      assert response.contentUtf8() == SUCCESS.body
    }

    and:
    assertTraces(count) {
      accessLogValue.waitForLoggedIds(count)
      assert accessLogValue.loggedIds.size() == count
      def loggedTraces = accessLogValue.loggedIds*.first
      def loggedSpans = accessLogValue.loggedIds*.second

      (0..count - 1).each {
        trace(it, 2) {
          serverSpan(it, 0, null, null, "GET", SUCCESS.body.length())
          controllerSpan(it, 1, span(0))
        }

        assert loggedTraces.contains(traces[it][0].traceId)
        assert loggedSpans.contains(traces[it][0].spanId)
      }
    }

    where:
    method = "GET"
    count << [1, 4] // make multiple requests.
  }

  def "access log has ids for error request"() {
    setup:
    assumeTrue(testError())
    def request = request(ERROR, method)
    def response = client.execute(request).aggregate().join()

    expect:
    response.status().code() == ERROR.status
    response.contentUtf8() == ERROR.body

    and:
    def spanCount = 2
    if (errorEndpointUsesSendError()) {
      spanCount++
    }
    assertTraces(1) {
      trace(0, spanCount) {
        serverSpan(it, 0, null, null, method, response.content().length(), ERROR)
        def spanIndex = 1
        controllerSpan(it, spanIndex, span(spanIndex - 1))
        spanIndex++
        if (errorEndpointUsesSendError()) {
          sendErrorSpan(it, spanIndex, span(spanIndex - 1))
          spanIndex++
        }
      }

      accessLogValue.waitForLoggedIds(1)
      def (String traceId, String spanId) = accessLogValue.loggedIds[0]
      assert traces[0][0].traceId == traceId
      assert traces[0][0].spanId == spanId
    }

    where:
    method = "GET"
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
  final List<Tuple2<String, String>> loggedIds = []

  TestAccessLogValve() {
    super(true)
  }

  void log(Request request, Response response, long time) {
    synchronized (loggedIds) {
      loggedIds.add(new Tuple2(request.getAttribute("trace_id"),
        request.getAttribute("span_id")))
      loggedIds.notifyAll()
    }
  }

  void waitForLoggedIds(int expected) {
    def timeout = TimeUnit.SECONDS.toMillis(20)
    def startTime = System.currentTimeMillis()
    def endTime = startTime + timeout
    def toWait = timeout
    synchronized (loggedIds) {
      while (loggedIds.size() < expected && toWait > 0) {
        loggedIds.wait(toWait)
        toWait = endTime - System.currentTimeMillis()
      }
      if (toWait <= 0) {
        throw new TimeoutException("Timeout waiting for " + expected + " access log ids, got " + loggedIds.size())
      }
    }
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

class TomcatServlet3TestFakeAsync extends TomcatServlet3Test {

  @Override
  Class<Servlet> servlet() {
    TestServlet3.FakeAsync
  }

  @Override
  boolean testException() {
    // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/807
    return false
  }
}

class TomcatServlet3TestForward extends TomcatDispatchTest {
  @Override
  Class<Servlet> servlet() {
    TestServlet3.Sync // dispatch to sync servlet
  }

  @Override
  boolean testNotFound() {
    false
  }

  @Override
  protected void setupServlets(Context context) {
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

class TomcatServlet3TestInclude extends TomcatDispatchTest {
  @Override
  Class<Servlet> servlet() {
    TestServlet3.Sync // dispatch to sync servlet
  }

  @Override
  boolean testNotFound() {
    false
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
  protected void setupServlets(Context context) {
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
    addServlet(context, "/dispatch" + CAPTURE_HEADERS.path, TestServlet3.DispatchImmediate)
    addServlet(context, "/dispatch" + INDEXED_CHILD.path, TestServlet3.DispatchImmediate)
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
    addServlet(context, "/dispatch" + CAPTURE_HEADERS.path, TestServlet3.DispatchAsync)
    addServlet(context, "/dispatch" + INDEXED_CHILD.path, TestServlet3.DispatchAsync)
    addServlet(context, "/dispatch/recursive", TestServlet3.DispatchRecursive)
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

abstract class TomcatDispatchTest extends TomcatServlet3Test {
  @Override
  URI buildAddress() {
    return new URI("http://localhost:$port$contextPath/dispatch/")
  }
}
