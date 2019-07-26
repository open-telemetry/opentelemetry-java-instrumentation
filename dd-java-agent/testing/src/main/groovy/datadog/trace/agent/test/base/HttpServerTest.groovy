package datadog.trace.agent.test.base

import datadog.opentracing.DDSpan
import datadog.trace.agent.decorator.HttpServerDecorator
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.asserts.ListWriterAssert
import datadog.trace.agent.test.asserts.TraceAssert
import datadog.trace.agent.test.utils.OkHttpUtils
import datadog.trace.agent.test.utils.PortUtils
import datadog.trace.api.DDSpanTypes
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import io.opentracing.tag.Tags
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import spock.lang.Shared

import java.util.concurrent.atomic.AtomicBoolean

import static datadog.trace.agent.test.asserts.TraceAssert.assertTrace
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.ERROR
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.NOT_FOUND
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.SUCCESS
import static datadog.trace.agent.test.utils.TraceUtils.basicSpan
import static datadog.trace.agent.test.utils.TraceUtils.runUnderTrace
import static org.junit.Assume.assumeTrue

abstract class HttpServerTest<DECORATOR extends HttpServerDecorator> extends AgentTestRunner {

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
    startServer(port)
  }

  abstract void startServer(int port)

  def cleanupSpec() {
    stopServer()
  }

  abstract void stopServer()

  abstract DECORATOR decorator()

  String expectedServiceName() {
    "unnamed-java-app"
  }

  abstract String expectedOperationName()

  boolean testNotFound() {
    true
  }

  enum ServerEndpoint {
    SUCCESS("success", 200, "success"),
    ERROR("error", 500, "controller error"),
    EXCEPTION("exception", 500, "controller exception"),
    REDIRECT("redirect", 302, null),
    NOT_FOUND("notFound", 404, "not found"),
    AUTH_REQUIRED("authRequired", 200, null),

    private final String path
    final int status
    final String body

    ServerEndpoint(String path, int status, String body) {
      this.path = path
      this.status = status
      this.body = body
    }

    String getPath() {
      return "/$path"
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
    return new Request.Builder()
      .url(HttpUrl.get(uri.resolve(address)))
      .method(method, body)
  }

  static <T> T controller(ServerEndpoint endpoint, Closure<T> closure) {
    if (endpoint == NOT_FOUND) {
      closure()
    } else {
      runUnderTrace("controller", closure)
    }
  }

  def "test success"() {
    setup:
    def request = request(SUCCESS, method, body).build()
    def response = client.newCall(request).execute()

    expect:
    response.code() == SUCCESS.status
    response.body().string() == SUCCESS.body

    and:
    cleanAndAssertTraces(1) {
      trace(0, 2) {
        serverSpan(it, 0)
        controllerSpan(it, 1, span(0))
      }
    }

    where:
    method = "GET"
    body = null
  }

  def "test success with parent"() {
    setup:
    def traceId = "123"
    def parentId = "456"
    def request = request(SUCCESS, method, body)
      .header("x-datadog-trace-id", traceId)
      .header("x-datadog-parent-id", parentId)
      .build()
    def response = client.newCall(request).execute()

    expect:
    response.code() == SUCCESS.status
    response.body().string() == SUCCESS.body

    and:
    cleanAndAssertTraces(1) {
      trace(0, 2) {
        serverSpan(it, 0, traceId, parentId)
        controllerSpan(it, 1, span(0))
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
      trace(0, 2) {
        serverSpan(it, 0, null, null, method, ERROR, true)
        controllerSpan(it, 1, span(0))
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
    response.body().string() == EXCEPTION.body

    and:
    cleanAndAssertTraces(1) {
      trace(0, 2) {
        serverSpan(it, 0, null, null, method, EXCEPTION, true)
        controllerSpan(it, 1, span(0), EXCEPTION.body)
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
      trace(0, 1) {
        serverSpan(it, 0, null, null, method, NOT_FOUND)
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
    TEST_WRITER.waitForTraces(size + 1)
    // TEST_WRITER is a CopyOnWriteArrayList, which doesn't support remove()
    def toRemove = TEST_WRITER.find {
      it.size() == 1 && it.get(0).operationName == "TEST_SPAN"
    }
    assertTrace(toRemove, 1) {
      basicSpan(it, 0, "TEST_SPAN", "ServerEntry")
    }
    TEST_WRITER.remove(toRemove)

    super.assertTraces(size, spec)
  }

  void controllerSpan(TraceAssert trace, int index, Object parent, String errorMessage = null) {
    trace.span(index) {
      serviceName expectedServiceName()
      operationName "controller"
      resourceName "controller"
      errored errorMessage != null
      childOf(parent as DDSpan)
      tags {
        defaultTags()
        if (errorMessage) {
          errorTags(Exception, errorMessage)
        }
      }
    }
  }

  // parent span must be cast otherwise it breaks debugging classloading (junit loads it early)
  void serverSpan(TraceAssert trace, int index, String traceID = null, String parentID = null, String method = "GET", ServerEndpoint endpoint = SUCCESS, boolean error = false) {
    trace.span(index) {
      serviceName expectedServiceName()
      operationName expectedOperationName()
      resourceName endpoint.status == 404 ? "404" : "$method ${endpoint.resolve(address).path}"
      spanType DDSpanTypes.HTTP_SERVER
      errored error
      if (parentID != null) {
        traceId traceID
        parentId parentID
      } else {
        parent()
      }
      tags {
        defaultTags(true)
        "$Tags.COMPONENT.key" serverDecorator.component()
        if (error) {
          "$Tags.ERROR.key" error
        }
        "$Tags.HTTP_STATUS.key" endpoint.status
        "$Tags.HTTP_URL.key" "${endpoint.resolve(address)}"
//        if (tagQueryString) {
//          "$DDTags.HTTP_QUERY" uri.query
//          "$DDTags.HTTP_FRAGMENT" { it == null || it == uri.fragment } // Optional
//        }
        "$Tags.PEER_HOSTNAME.key" "localhost"
        "$Tags.PEER_PORT.key" Integer
        "$Tags.PEER_HOST_IPV4.key" { it == null || it == "127.0.0.1" } // Optional
        "$Tags.HTTP_METHOD.key" method
        "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
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
