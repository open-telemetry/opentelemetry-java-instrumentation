import datadog.opentracing.DDSpan
import datadog.opentracing.DDTracer
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.TestUtils
import datadog.trace.api.DDSpanTypes
import datadog.trace.common.writer.ListWriter
import io.opentracing.util.GlobalTracer
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.eclipse.jetty.http.HttpHeaders
import org.eclipse.jetty.security.ConstraintMapping
import org.eclipse.jetty.security.ConstraintSecurityHandler
import org.eclipse.jetty.security.HashLoginService
import org.eclipse.jetty.security.LoginService
import org.eclipse.jetty.security.authentication.BasicAuthenticator
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.util.security.Constraint

import java.lang.reflect.Field
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import static datadog.trace.agent.test.ListWriterAssert.assertTraces

class JettyServlet3Test extends AgentTestRunner {

  static final int PORT = TestUtils.randomOpenPort()

  // Jetty needs this to ensure consistent ordering for async.
  CountDownLatch latch = new CountDownLatch(1)

  OkHttpClient client = new OkHttpClient.Builder()
    .addNetworkInterceptor(new Interceptor() {
    @Override
    Response intercept(Interceptor.Chain chain) throws IOException {
      def response = chain.proceed(chain.request())
      JettyServlet3Test.this.latch.await(10, TimeUnit.SECONDS) // don't block forever or test never fails.
      return response
    }
  })
  // Uncomment when debugging:
  //  .connectTimeout(1, TimeUnit.HOURS)
  //  .writeTimeout(1, TimeUnit.HOURS)
  //  .readTimeout(1, TimeUnit.HOURS)
    .build()

  private Server jettyServer
  private ServletContextHandler servletContext

  ListWriter writer = new ListWriter() {
    @Override
    void write(final List<DDSpan> trace) {
      add(trace)
      JettyServlet3Test.this.latch.countDown()
    }
  }
  DDTracer tracer = new DDTracer(writer)

  def setup() {
    jettyServer = new Server(PORT)
    servletContext = new ServletContextHandler()

    ConstraintSecurityHandler security = setupAuthentication(jettyServer)

    servletContext.setSecurityHandler(security)
    servletContext.addServlet(TestServlet3.Sync, "/sync")
    servletContext.addServlet(TestServlet3.Sync, "/auth/sync")
    servletContext.addServlet(TestServlet3.Async, "/async")
    servletContext.addServlet(TestServlet3.Async, "/auth/async")

    jettyServer.setHandler(servletContext)
    jettyServer.start()

    System.out.println(
      "Jetty server: http://localhost:" + PORT + "/")

    try {
      GlobalTracer.register(tracer)
    } catch (final Exception e) {
      // Force it anyway using reflection
      final Field field = GlobalTracer.getDeclaredField("tracer")
      field.setAccessible(true)
      field.set(null, tracer)
    }
    writer.start()
    assert GlobalTracer.isRegistered()
  }

  def cleanup() {
    jettyServer.stop()
    jettyServer.destroy()
  }

  def "test #path servlet call"() {
    setup:
    def requestBuilder = new Request.Builder()
      .url("http://localhost:$PORT/$path")
      .get()
    if (auth) {
      requestBuilder.header(HttpHeaders.AUTHORIZATION, Credentials.basic("user", "password"))
    }
    def response = client.newCall(requestBuilder.build()).execute()

    expect:
    response.body().string().trim() == expectedResponse

    assertTraces(writer, 1) {
      trace(0, 1) {
        span(0) {
          serviceName "unnamed-java-app"
          operationName "servlet.request"
          resourceName "GET /$path"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          parent()
          tags {
            "http.url" "http://localhost:$PORT/$path"
            "http.method" "GET"
            "span.kind" "server"
            "component" "java-web-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" ""
            "http.status_code" 200
            if (auth) {
              "user.principal" "user"
            }
            defaultTags()
          }
        }
      }
    }

    where:
    path         | expectedResponse | auth
    "async"      | "Hello Async"    | false
    "sync"       | "Hello Sync"     | false
    "auth/async" | "Hello Async"    | true
    "auth/sync"  | "Hello Sync"     | true
  }

  def "servlet instrumentation clears state after async request"() {
    setup:
    def request = new Request.Builder()
      .url("http://localhost:$PORT/async")
      .get()
      .build()
    def numTraces = 5
    for (int i = 0; i < numTraces; ++i) {
      client.newCall(request).execute()
    }

    expect:
    assertTraces(writer, numTraces) {
      for (int i = 0; i < numTraces; ++i) {
        trace(i, 1) {
          span(0) {
            serviceName "unnamed-java-app"
            operationName "servlet.request"
            resourceName "GET /async"
          }
        }
      }
    }
  }

  def "test #path error servlet call"() {
    setup:
    def request = new Request.Builder()
      .url("http://localhost:$PORT/$path?error=true")
      .get()
      .build()
    def response = client.newCall(request).execute()

    expect:
    response.body().string().trim() != expectedResponse

    assertTraces(writer, 1) {
      trace(0, 1) {
        span(0) {
          serviceName "unnamed-java-app"
          operationName "servlet.request"
          resourceName "GET /$path"
          spanType DDSpanTypes.WEB_SERVLET
          errored true
          parent()
          tags {
            "http.url" "http://localhost:$PORT/$path"
            "http.method" "GET"
            "span.kind" "server"
            "component" "java-web-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" ""
            "http.status_code" 500
            errorTags(RuntimeException, "some $path error")
            defaultTags()
          }
        }
      }
    }

    where:
    path   | expectedResponse
    //"async" | "Hello Async" // FIXME: I can't seem get the async error handler to trigger
    "sync" | "Hello Sync"
  }

  def "test #path non-throwing-error servlet call"() {
    setup:
    def request = new Request.Builder()
      .url("http://localhost:$PORT/$path?non-throwing-error=true")
      .get()
      .build()
    def response = client.newCall(request).execute()

    expect:
    response.body().string().trim() != expectedResponse

    assertTraces(writer, 1) {
      trace(0, 1) {
        span(0) {
          serviceName "unnamed-java-app"
          operationName "servlet.request"
          resourceName "GET /$path"
          spanType DDSpanTypes.WEB_SERVLET
          errored true
          parent()
          tags {
            "http.url" "http://localhost:$PORT/$path"
            "http.method" "GET"
            "span.kind" "server"
            "component" "java-web-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" ""
            "http.status_code" 500
            "error" true
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
