import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.TestUtils
import datadog.trace.agent.test.utils.OkHttpUtils
import datadog.trace.api.DDSpanTypes
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.eclipse.jetty.http.HttpHeaders
import org.eclipse.jetty.http.security.Constraint
import org.eclipse.jetty.security.ConstraintMapping
import org.eclipse.jetty.security.ConstraintSecurityHandler
import org.eclipse.jetty.security.HashLoginService
import org.eclipse.jetty.security.LoginService
import org.eclipse.jetty.security.authentication.BasicAuthenticator
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.ServletContextHandler

import static datadog.trace.agent.test.asserts.ListWriterAssert.assertTraces

class JettyServlet2Test extends AgentTestRunner {

  OkHttpClient client = OkHttpUtils.clientBuilder().addNetworkInterceptor(new Interceptor() {
    @Override
    Response intercept(Interceptor.Chain chain) throws IOException {
      def response = chain.proceed(chain.request())
      TEST_WRITER.waitForTraces(1)
      return response
    }
  })
    .build()

  int port
  private Server jettyServer
  private ServletContextHandler servletContext

  def setup() {
    port = TestUtils.randomOpenPort()
    jettyServer = new Server(port)
    servletContext = new ServletContextHandler()
    servletContext.contextPath = "/ctx"

    ConstraintSecurityHandler security = setupAuthentication(jettyServer)

    servletContext.setSecurityHandler(security)
    servletContext.addServlet(TestServlet2.Sync, "/sync")
    servletContext.addServlet(TestServlet2.Sync, "/auth/sync")

    jettyServer.setHandler(servletContext)
    jettyServer.start()

    System.out.println(
      "Jetty server: http://localhost:" + port + "/")
  }

  def cleanup() {
    jettyServer.stop()
    jettyServer.destroy()
  }

  def "test #path servlet call"() {
    setup:
    def requestBuilder = new Request.Builder()
      .url("http://localhost:$port/ctx/$path")
      .get()
    if (auth) {
      requestBuilder.header(HttpHeaders.AUTHORIZATION, Credentials.basic("user", "password"))
    }
    def response = client.newCall(requestBuilder.build()).execute()

    expect:
    response.body().string().trim() == expectedResponse

    assertTraces(TEST_WRITER, 1) {
      trace(0, 1) {
        span(0) {
          serviceName "ctx"
          operationName "servlet.request"
          resourceName "GET /ctx/$path"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          parent()
          tags {
            "http.url" "http://localhost:$port/ctx/$path"
            "http.method" "GET"
            "span.kind" "server"
            "component" "java-web-servlet"
            "span.origin.type" "TestServlet2\$Sync"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/ctx"
            if (auth) {
              "user.principal" "user"
            }
            defaultTags()
          }
        }
      }
    }

    where:
    path        | expectedResponse | auth
    "sync"      | "Hello Sync"     | false
    "auth/sync" | "Hello Sync"     | true
  }

  def "test #path error servlet call"() {
    setup:
    def request = new Request.Builder()
      .url("http://localhost:$port/ctx/$path?error=true")
      .get()
      .build()
    def response = client.newCall(request).execute()

    expect:
    response.body().string().trim() != expectedResponse

    assertTraces(TEST_WRITER, 1) {
      trace(0, 1) {
        span(0) {
          serviceName "ctx"
          operationName "servlet.request"
          resourceName "GET /ctx/$path"
          spanType DDSpanTypes.WEB_SERVLET
          errored true
          parent()
          tags {
            "http.url" "http://localhost:$port/ctx/$path"
            "http.method" "GET"
            "span.kind" "server"
            "component" "java-web-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "span.origin.type" "TestServlet2\$Sync"
            "servlet.context" "/ctx"
            errorTags(RuntimeException, "some $path error")
            defaultTags()
          }
        }
      }
    }

    where:
    path   | expectedResponse
    "sync" | "Hello Sync"
  }

  def "test #path non-throwing-error servlet call"() {
    // This doesn't actually detect the error because we can't get the status code via the old servlet API.
    setup:
    def request = new Request.Builder()
      .url("http://localhost:$port/ctx/$path?non-throwing-error=true")
      .get()
      .build()
    def response = client.newCall(request).execute()

    expect:
    response.body().string().trim() != expectedResponse

    assertTraces(TEST_WRITER, 1) {
      trace(0, 1) {
        span(0) {
          serviceName "ctx"
          operationName "servlet.request"
          resourceName "GET /ctx/$path"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          parent()
          tags {
            "http.url" "http://localhost:$port/ctx/$path"
            "http.method" "GET"
            "span.kind" "server"
            "component" "java-web-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "span.origin.type" "TestServlet2\$Sync"
            "servlet.context" "/ctx"
            defaultTags()
          }
        }
      }
    }

    where:
    path   | expectedResponse
    "sync" | "Hello Sync"
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
  private ConstraintSecurityHandler setupAuthentication(Server jettyServer) {
    ConstraintSecurityHandler security = new ConstraintSecurityHandler()

    Constraint constraint = new Constraint()
    constraint.setName("auth")
    constraint.setAuthenticate(true)
    constraint.setRoles("role")

    ConstraintMapping mapping = new ConstraintMapping()
    mapping.setPathSpec("/auth/*")
    mapping.setConstraint(constraint)

    security.setConstraintMappings(mapping)
    security.setAuthenticator(new BasicAuthenticator())

    LoginService loginService = new HashLoginService("TestRealm",
      "src/test/resources/realm.properties")
    security.setLoginService(loginService)
    jettyServer.addBean(loginService)

    security
  }
}
