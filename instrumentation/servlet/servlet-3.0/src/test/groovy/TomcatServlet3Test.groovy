/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.google.common.io.Files
import javax.servlet.Servlet
import javax.servlet.ServletException
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

import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.AUTH_REQUIRED
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.ERROR
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.QUERY_PARAM
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.SUCCESS
import static io.opentelemetry.auto.test.utils.TraceUtils.basicSpan

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

    File applicationDir = new File(baseDir, "/webapps/ROOT")
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
  protected String entryPointName() {
    return 'ApplicationFilterChain.doFilter'
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
    assertTraces(count * 2) {
      assert accessLogValue.loggedIds.size() == count
      def loggedTraces = accessLogValue.loggedIds*.first
      def loggedSpans = accessLogValue.loggedIds*.second

      (0..count - 1).each {
        trace(it * 2, 1) {
          basicSpan(it, 0, "TEST_SPAN")
        }
        trace(it * 2 + 1, 2) {
          serverSpan(it, 0)
          controllerSpan(it, 1, span(0))
        }

        assert loggedTraces.contains(traces[it * 2 + 1][0].traceId.toLowerBase16())
        assert loggedSpans.contains(traces[it * 2 + 1][0].spanId.toLowerBase16())
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
    assertTraces(2) {
      trace(0, 1) {
        basicSpan(it, 0, "TEST_SPAN")
      }
      trace(1, 2) {
        serverSpan(it, 0, null, null, method, response.body().contentLength(), ERROR)
        controllerSpan(it, 1, span(0))
      }

      def (String traceId, String spanId) = accessLogValue.loggedIds[0]
      assert traces[1][0].traceId.toLowerBase16() == traceId
      assert traces[1][0].spanId.toLowerBase16() == spanId
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
  List<Tuple2<String, String>> loggedIds = Collections.synchronizedList([])

  TestAccessLogValve() {
    super(true)
  }

  void log(Request request, Response response, long time) {
    loggedIds.add(new Tuple2(request.getAttribute("traceId"),
      request.getAttribute("spanId")))
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
}
