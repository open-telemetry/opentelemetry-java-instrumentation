package datadog.trace.agent.test.base

import ch.qos.logback.classic.Level
import datadog.opentracing.DDSpan
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.asserts.ListWriterAssert
import datadog.trace.agent.test.asserts.TraceAssert
import datadog.trace.agent.test.utils.OkHttpUtils
import datadog.trace.agent.test.utils.PortUtils
import datadog.trace.api.DDSpanTypes
import datadog.trace.api.DDTags
import datadog.trace.bootstrap.instrumentation.api.Tags
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.Shared
import spock.lang.Unroll

import java.util.concurrent.atomic.AtomicBoolean

import static datadog.trace.agent.test.asserts.TraceAssert.assertTrace
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.*
import static datadog.trace.agent.test.utils.ConfigUtils.withConfigOverride
import static datadog.trace.agent.test.utils.TraceUtils.basicSpan
import static datadog.trace.agent.test.utils.TraceUtils.runUnderTrace
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activeScope
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activeSpan
import static org.junit.Assume.assumeTrue

@Unroll
abstract class HttpServerTest<SERVER> extends AgentTestRunner {

  public static final Logger SERVER_LOGGER = LoggerFactory.getLogger("http-server")
  static {
    ((ch.qos.logback.classic.Logger) SERVER_LOGGER).setLevel(Level.DEBUG)
  }

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
  String component = component()

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

  abstract String component()

  String expectedServiceName() {
    "unnamed-java-app"
  }

  abstract String expectedOperationName()

  boolean hasHandlerSpan() {
    false
  }

  // Return the handler span's name
  String reorderHandlerSpan() {
    null
  }

  boolean reorderControllerSpan() {
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
    assert activeScope().asyncPropagating: "Scope should be propagating async."
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
    cleanAndAssertTraces(count) {
      (1..count).eachWithIndex { val, i ->
        if (hasHandlerSpan()) {
          trace(i, 3) {
            serverSpan(it, 0)
            handlerSpan(it, 1, span(0))
            controllerSpan(it, 2, span(1))
          }
        } else {
          trace(i, 2) {
            serverSpan(it, 0)
            controllerSpan(it, 1, span(0))
          }
        }
      }
    }

    where:
    method = "GET"
    body = null
    count << [1, 4, 50] // make multiple requests.
  }

  def "test success with parent"() {
    setup:
    def traceId = 123G
    def parentId = 456G
    def request = request(SUCCESS, method, body)
      .header("x-datadog-trace-id", traceId.toString())
      .header("x-datadog-parent-id", parentId.toString())
      .build()
    def response = client.newCall(request).execute()

    expect:
    response.code() == SUCCESS.status
    response.body().string() == SUCCESS.body

    and:
    cleanAndAssertTraces(1) {
      if (hasHandlerSpan()) {
        trace(0, 3) {
          serverSpan(it, 0, traceId, parentId)
          handlerSpan(it, 1, span(0))
          controllerSpan(it, 2, span(1))
        }
      } else {
        trace(0, 2) {
          serverSpan(it, 0, traceId, parentId)
          controllerSpan(it, 1, span(0))
        }
      }
    }

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
    cleanAndAssertTraces(1) {
      if (hasHandlerSpan()) {
        trace(0, 3) {
          serverSpan(it, 0, null, null, "GET", endpoint)
          handlerSpan(it, 1, span(0), endpoint)
          controllerSpan(it, 2, span(1))
        }
      } else {
        trace(0, 2) {
          serverSpan(it, 0, null, null, "GET", endpoint)
          controllerSpan(it, 1, span(0))
        }
      }
    }

    where:
    method = "GET"
    body = null
    endpoint << [SUCCESS, QUERY_PARAM]
  }

  def "test success with multiple header attached parent"() {
    setup:
    def traceId = 123G
    def parentId = 456G
    def request = request(SUCCESS, method, body)
      .header("x-datadog-trace-id", traceId.toString() + ", " + traceId.toString())
      .header("x-datadog-parent-id", parentId.toString() + ", " + parentId.toString())
      .header("x-datadog-sampling-priority", "1, 1")
      .build()
    def response = client.newCall(request).execute()

    expect:
    response.code() == SUCCESS.status
    response.body().string() == SUCCESS.body

    and:
    cleanAndAssertTraces(1) {
      if (hasHandlerSpan()) {
        trace(0, 3) {
          serverSpan(it, 0, traceId, parentId)
          handlerSpan(it, 1, span(0))
          controllerSpan(it, 2, span(1))
        }
      } else {
        trace(0, 2) {
          serverSpan(it, 0, traceId, parentId)
          controllerSpan(it, 1, span(0))
        }
      }
    }

    where:
    method = "GET"
    body = null
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
    cleanAndAssertTraces(1) {
      if (hasHandlerSpan()) {
        trace(0, 3) {
          serverSpan(it, 0, null, null, method, REDIRECT)
          handlerSpan(it, 1, span(0), REDIRECT)
          controllerSpan(it, 2, span(1))
        }
      } else {
        trace(0, 2) {
          serverSpan(it, 0, null, null, method, REDIRECT)
          controllerSpan(it, 1, span(0))
        }
      }
    }

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
    cleanAndAssertTraces(1) {
      if (hasHandlerSpan()) {
        trace(0, 3) {
          serverSpan(it, 0, null, null, method, ERROR)
          handlerSpan(it, 1, span(0), ERROR)
          controllerSpan(it, 2, span(1))
        }
      } else {
        trace(0, 2) {
          serverSpan(it, 0, null, null, method, ERROR)
          controllerSpan(it, 1, span(0))
        }
      }
    }

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
    cleanAndAssertTraces(1) {
      if (hasHandlerSpan()) {
        trace(0, 3) {
          serverSpan(it, 0, null, null, method, EXCEPTION)
          handlerSpan(it, 1, span(0), EXCEPTION)
          controllerSpan(it, 2, span(1), EXCEPTION.body)
        }
      } else {
        trace(0, 2) {
          serverSpan(it, 0, null, null, method, EXCEPTION)
          controllerSpan(it, 1, span(0), EXCEPTION.body)
        }
      }
    }

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
    cleanAndAssertTraces(1) {
      if (hasHandlerSpan()) {
        trace(0, 2) {
          serverSpan(it, 0, null, null, method, NOT_FOUND)
          handlerSpan(it, 1, span(0), NOT_FOUND)
        }
      } else {
        trace(0, 1) {
          serverSpan(it, 0, null, null, method, NOT_FOUND)
        }
      }
    }

    where:
    method = "GET"
    body = null
  }

  //FIXME: add tests for POST with large/chunked data

  void cleanAndAssertTraces(
    final int size,
    @ClosureParams(value = SimpleType, options = "datadog.trace.agent.test.asserts.ListWriterAssert")
    @DelegatesTo(value = ListWriterAssert, strategy = Closure.DELEGATE_FIRST)
    final Closure spec) {

    // If this is failing, make sure HttpServerTestAdvice is applied correctly.
    TEST_WRITER.waitForTraces(size * 2)
    // TEST_WRITER is a CopyOnWriteArrayList, which doesn't support remove()
    def toRemove = TEST_WRITER.findAll {
      it.size() == 1 && it.get(0).operationName == "TEST_SPAN"
    }
    toRemove.each {
      assertTrace(it, 1) {
        basicSpan(it, 0, "TEST_SPAN", "ServerEntry")
      }
    }
    assert toRemove.size() == size
    TEST_WRITER.removeAll(toRemove)

    if (reorderHandlerSpan()) {
      TEST_WRITER.each {
        def controllerSpan = it.find {
          it.operationName == reorderHandlerSpan()
        }
        if (controllerSpan) {
          it.remove(controllerSpan)
          it.add(controllerSpan)
        }
      }
    }

    if (reorderControllerSpan() || reorderHandlerSpan()) {
      // Some frameworks close the handler span before the controller returns, so we need to manually reorder it.
      TEST_WRITER.each {
        def controllerSpan = it.find {
          it.operationName == "controller"
        }
        if (controllerSpan) {
          it.remove(controllerSpan)
          it.add(controllerSpan)
        }
      }
    }

    assertTraces(size, spec)
  }

  void controllerSpan(TraceAssert trace, int index, Object parent, String errorMessage = null) {
    trace.span(index) {
      serviceName expectedServiceName()
      operationName "controller"
      resourceName "controller"
      errored errorMessage != null
      childOf(parent as DDSpan)
      tags {
        if (errorMessage) {
          errorTags(Exception, errorMessage)
        }
        defaultTags()
      }
    }
  }

  void handlerSpan(TraceAssert trace, int index, Object parent, ServerEndpoint endpoint = SUCCESS) {
    throw new UnsupportedOperationException("handlerSpan not implemented in " + getClass().name)
  }

  // parent span must be cast otherwise it breaks debugging classloading (junit loads it early)
  void serverSpan(TraceAssert trace, int index, BigInteger traceID = null, BigInteger parentID = null, String method = "GET", ServerEndpoint endpoint = SUCCESS) {
    trace.span(index) {
      serviceName expectedServiceName()
      operationName expectedOperationName()
      resourceName endpoint.status == 404 ? "404" : "$method ${endpoint.resolve(address).path}"
      spanType DDSpanTypes.HTTP_SERVER
      errored endpoint.errored
      if (parentID != null) {
        traceId traceID
        parentId parentID
      } else {
        parent()
      }
      tags {
        "$Tags.COMPONENT" component
        "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
        "$Tags.PEER_PORT" { it == null || it instanceof Integer } // Optional
        "$Tags.PEER_HOST_IPV4" { it == null || it == "127.0.0.1" } // Optional
        "$Tags.HTTP_URL" "${endpoint.resolve(address)}"
        "$Tags.HTTP_METHOD" method
        "$Tags.HTTP_STATUS" endpoint.status
        if (endpoint.errored) {
          "$Tags.ERROR" endpoint.errored
        }
        if (endpoint.query) {
          "$DDTags.HTTP_QUERY" endpoint.query
        }
        // OkHttp never sends the fragment in the request.
//        if (endpoint.fragment) {
//          "$DDTags.HTTP_FRAGMENT" endpoint.fragment
//        }
        defaultTags(true)
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
