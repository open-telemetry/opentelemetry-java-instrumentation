package io.opentelemetry.auto.test.base

import io.opentelemetry.auto.api.MoreTags
import io.opentelemetry.auto.api.SpanTypes
import io.opentelemetry.auto.decorator.HttpServerDecorator
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.auto.test.asserts.TraceAssert
import io.opentelemetry.auto.test.utils.OkHttpUtils
import io.opentelemetry.auto.test.utils.PortUtils
import io.opentelemetry.sdk.trace.SpanData
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import spock.lang.Shared
import spock.lang.Unroll

import java.util.concurrent.atomic.AtomicBoolean

import static io.opentelemetry.auto.instrumentation.api.AgentTracer.activeSpan
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.ERROR
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.NOT_FOUND
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.QUERY_PARAM
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.SUCCESS
import static io.opentelemetry.auto.test.utils.ConfigUtils.withConfigOverride
import static io.opentelemetry.auto.test.utils.TraceUtils.basicSpan
import static io.opentelemetry.auto.test.utils.TraceUtils.runUnderTrace
import static org.junit.Assume.assumeTrue

@Unroll
abstract class HttpServerTest<SERVER, DECORATOR extends HttpServerDecorator> extends AgentTestRunner {

  @Shared
  SERVER server
  @Shared
  OkHttpClient client = OkHttpUtils.client()
  @Shared
  int port = PortUtils.randomOpenPort()
  @Shared
  URI address = buildAddress()

  URI buildAddress() {
    return new URI("http://localhost:$port/")
  }

  @Shared
  DECORATOR serverDecorator = decorator()

  def setupSpec() {
    server = startServer(port)
    println getClass().name + " http server started at: http://localhost:$port/"
  }

  abstract SERVER startServer(int port)

  def cleanupSpec() {
    if (server == null) {
      println getClass().name + " can't stop null server"
      return
    }
    stopServer(server)
    server = null
    println getClass().name + " http server stopped at: http://localhost:$port/"
  }

  abstract void stopServer(SERVER server)

  abstract DECORATOR decorator()

  abstract String expectedOperationName()

  boolean hasHandlerSpan() {
    false
  }

  boolean hasRenderSpan(ServerEndpoint endpoint) {
    false
  }

  boolean hasDispatchSpan(ServerEndpoint endpoint) {
    false
  }

  boolean redirectHasBody() {
    false
  }

  boolean testNotFound() {
    true
  }

  boolean testExceptionBody() {
    true
  }

  enum ServerEndpoint {
    SUCCESS("success", 200, "success"),
    REDIRECT("redirect", 302, "/redirected"),
    ERROR("error-status", 500, "controller error"), // "error" is a special path for some frameworks
    EXCEPTION("exception", 500, "controller exception"),
    NOT_FOUND("notFound", 404, "not found"),

    // TODO: add tests for the following cases:
    QUERY_PARAM("query?some=query", 200, "some=query"),
    // OkHttp never sends the fragment in the request, so these cases don't work.
//    FRAGMENT_PARAM("fragment#some-fragment", 200, "some-fragment"),
//    QUERY_FRAGMENT_PARAM("query/fragment?some=query#some-fragment", 200, "some=query#some-fragment"),
    PATH_PARAM("path/123/param", 200, "123"),
    AUTH_REQUIRED("authRequired", 200, null),

    private final String path
    final String query
    final String fragment
    final int status
    final String body
    final Boolean errored

    ServerEndpoint(String uri, int status, String body) {
      def uriObj = URI.create(uri)
      this.path = uriObj.path
      this.query = uriObj.query
      this.fragment = uriObj.fragment
      this.status = status
      this.body = body
      this.errored = status >= 500
    }

    String getPath() {
      return "/$path"
    }

    String rawPath() {
      return path
    }

    URI resolve(URI address) {
      return address.resolve(path)
    }

    private static final Map<String, ServerEndpoint> PATH_MAP = values().collectEntries { [it.path, it] }

    static ServerEndpoint forPath(String path) {
      return PATH_MAP.get(path)
    }
  }

  Request.Builder request(ServerEndpoint uri, String method, String body) {
    def url = HttpUrl.get(uri.resolve(address)).newBuilder()
      .query(uri.query)
      .fragment(uri.fragment)
      .build()
    return new Request.Builder()
      .url(url)
      .method(method, body)
  }

  static <T> T controller(ServerEndpoint endpoint, Closure<T> closure) {
    assert activeSpan() != null: "Controller should have a parent span."
    if (endpoint == NOT_FOUND) {
      return closure()
    }
    return runUnderTrace("controller", closure)
  }

  def "test success with #count requests"() {
    setup:
    def request = request(SUCCESS, method, body).build()
    List<Response> responses = (1..count).collect {
      return client.newCall(request).execute()
    }

    expect:
    responses.each { response ->
      assert response.code() == SUCCESS.status
      assert response.body().string() == SUCCESS.body
    }

    and:
    assertTheTraces(count)

    where:
    method = "GET"
    body = null
    count << [1, 4, 50] // make multiple requests.
  }

  def "test success with parent"() {
    setup:
    def traceId = "00000000000000000000000000000123"
    def parentId = "0000000000000456"
    def request = request(SUCCESS, method, body)
      .header("traceparent", "00-" + traceId.toString() + "-" + parentId.toString() + "-01")
      .build()
    def response = client.newCall(request).execute()

    expect:
    response.code() == SUCCESS.status
    response.body().string() == SUCCESS.body

    and:
    assertTheTraces(1, traceId, parentId)

    where:
    method = "GET"
    body = null
  }

  def "test tag query string for #endpoint"() {
    setup:
    def request = request(endpoint, method, body).build()
    Response response = withConfigOverride("http.server.tag.query-string", "true") {
      client.newCall(request).execute()
    }

    expect:
    response.code() == endpoint.status
    response.body().string() == endpoint.body

    and:
    assertTheTraces(1, null, null, method, endpoint)

    where:
    method = "GET"
    body = null
    endpoint << [SUCCESS, QUERY_PARAM]
  }

  def "test redirect"() {
    setup:
    def request = request(REDIRECT, method, body).build()
    def response = client.newCall(request).execute()

    expect:
    response.code() == REDIRECT.status
    response.header("location") == REDIRECT.body ||
      response.header("location") == "${address.resolve(REDIRECT.body)}"
    response.body().contentLength() < 1 || redirectHasBody()

    and:
    assertTheTraces(1, null, null, method, REDIRECT)

    where:
    method = "GET"
    body = null
  }

  def "test error"() {
    setup:
    def request = request(ERROR, method, body).build()
    def response = client.newCall(request).execute()

    expect:
    response.code() == ERROR.status
    response.body().string() == ERROR.body

    and:
    assertTheTraces(1, null, null, method, ERROR)

    where:
    method = "GET"
    body = null
  }

  def "test exception"() {
    setup:
    def request = request(EXCEPTION, method, body).build()
    def response = client.newCall(request).execute()

    expect:
    response.code() == EXCEPTION.status
    if (testExceptionBody()) {
      assert response.body().string() == EXCEPTION.body
    }

    and:
    assertTheTraces(1, null, null, method, EXCEPTION, EXCEPTION.body)

    where:
    method = "GET"
    body = null
  }

  def "test notFound"() {
    setup:
    assumeTrue(testNotFound())
    def request = request(NOT_FOUND, method, body).build()
    def response = client.newCall(request).execute()

    expect:
    response.code() == NOT_FOUND.status

    and:
    assertTheTraces(1, null, null, method, NOT_FOUND)

    where:
    method = "GET"
    body = null
  }

  //FIXME: add tests for POST with large/chunked data

  void assertTheTraces(int size, String traceID = null, String parentID = null, String method = "GET", ServerEndpoint endpoint = SUCCESS, String errorMessage = null) {
    def spanCount = 1 // server span
    if (hasDispatchSpan(endpoint)) {
      spanCount++
    }
    if (hasHandlerSpan()) {
      spanCount++
    }
    if (endpoint != NOT_FOUND) {
      spanCount++ // controller span
      if (hasRenderSpan(endpoint)) {
        spanCount++
      }
    }
    assertTraces(size * 2) {
      (0..size - 1).each {
        trace(it * 2, 1) {
          basicSpan(it, 0, "TEST_SPAN", "ServerEntry")
        }
        trace(it * 2 + 1, spanCount) {
          def spanIndex = 0
          serverSpan(it, spanIndex++, traceID, parentID, method, endpoint)
          if (hasDispatchSpan(endpoint)) {
            dispatchSpan(it, spanIndex++, span(0), method, endpoint)
          }
          if (hasHandlerSpan()) {
            handlerSpan(it, spanIndex++, span(0), method, endpoint)
          }
          if (endpoint != NOT_FOUND) {
            if (hasHandlerSpan() || hasDispatchSpan(endpoint)) { // currently there are no tests which have both
              controllerSpan(it, spanIndex++, span(1), errorMessage)
            } else {
              controllerSpan(it, spanIndex++, span(0), errorMessage)
            }
            if (hasRenderSpan(endpoint)) {
              renderSpan(it, spanIndex++, span(0), method, endpoint)
            }
          }
        }
      }
    }
  }

  void controllerSpan(TraceAssert trace, int index, Object parent, String errorMessage = null) {
    trace.span(index) {
      operationName "controller"
      errored errorMessage != null
      childOf((SpanData) parent)
      tags {
        if (errorMessage) {
          errorTags(Exception, errorMessage)
        }
      }
    }
  }

  void handlerSpan(TraceAssert trace, int index, Object parent, String method = "GET", ServerEndpoint endpoint = SUCCESS) {
    throw new UnsupportedOperationException("handlerSpan not implemented in " + getClass().name)
  }

  void renderSpan(TraceAssert trace, int index, Object parent, String method = "GET", ServerEndpoint endpoint = SUCCESS) {
    throw new UnsupportedOperationException("renderSpan not implemented in " + getClass().name)
  }

  void dispatchSpan(TraceAssert trace, int index, Object parent, String method = "GET", ServerEndpoint endpoint = SUCCESS) {
    throw new UnsupportedOperationException("dispatchSpan not implemented in " + getClass().name)
  }

  // parent span must be cast otherwise it breaks debugging classloading (junit loads it early)
  void serverSpan(TraceAssert trace, int index, String traceID = null, String parentID = null, String method = "GET", ServerEndpoint endpoint = SUCCESS) {
    trace.span(index) {
      operationName expectedOperationName()
      errored endpoint.errored
      if (parentID != null) {
        traceId traceID
        parentId parentID
      } else {
        parent()
      }
      tags {
        "$MoreTags.SPAN_TYPE" SpanTypes.HTTP_SERVER
        "$Tags.COMPONENT" serverDecorator.component()
        "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
        "$Tags.PEER_HOSTNAME" { it == "localhost" || it == "127.0.0.1" }
        "$Tags.PEER_PORT" Long
        "$Tags.PEER_HOST_IPV4" { it == null || it == "127.0.0.1" } // Optional
        "$Tags.HTTP_URL" "${endpoint.resolve(address)}"
        "$Tags.HTTP_METHOD" method
        "$Tags.HTTP_STATUS" endpoint.status
        if (endpoint.query) {
          "$MoreTags.HTTP_QUERY" endpoint.query
        }
        // OkHttp never sends the fragment in the request.
//        if (endpoint.fragment) {
//          "$MoreTags.HTTP_FRAGMENT" endpoint.fragment
//        }
      }
    }
  }

  public static final AtomicBoolean ENABLE_TEST_ADVICE = new AtomicBoolean(false)

  def setup() {
    ENABLE_TEST_ADVICE.set(true)
  }

  def cleanup() {
    ENABLE_TEST_ADVICE.set(false)
  }
}
