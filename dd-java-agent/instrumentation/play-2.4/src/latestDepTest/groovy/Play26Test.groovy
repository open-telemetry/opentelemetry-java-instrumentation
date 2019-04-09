import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.utils.OkHttpUtils
import datadog.trace.agent.test.utils.PortUtils
import datadog.trace.api.DDSpanTypes
import okhttp3.Request
import play.api.test.TestServer
import play.test.Helpers
import spock.lang.Shared

class Play26Test extends AgentTestRunner {
  @Shared
  int port = PortUtils.randomOpenPort()
  @Shared
  TestServer testServer

  @Shared
  def client = OkHttpUtils.client()

  def setupSpec() {
    testServer = Helpers.testServer(port, Play26TestUtils.buildTestApp())
    testServer.start()
  }

  def cleanupSpec() {
    testServer.stop()
  }

  def "request traces"() {
    setup:
    def request = new Request.Builder()
      .url("http://localhost:$port/$path")
      .header("x-datadog-trace-id", "123")
      .header("x-datadog-parent-id", "456")
      .get()
      .build()
    def response = client.newCall(request).execute()

    expect:
    testServer != null
    response.code() == status
    if (body instanceof Class) {
      body.isInstance(response.body())
    } else {
      response.body().string() == body
    }

    assertTraces(1) {
      trace(0, extraSpans ? 3 : 2) {
        span(0) {
          traceId "123"
          parentId "456"
          serviceName "unnamed-java-app"
          operationName "akka-http.request"
          resourceName status == 404 ? "404" : "GET $route"
          spanType DDSpanTypes.HTTP_SERVER
          errored isError
          tags {
            "http.status_code" status
            "http.url" "http://localhost:$port/$path"
            "http.method" "GET"
            "span.kind" "server"
            "component" "akka-http-server"
            if (isError) {
              "error" true
            }
            defaultTags(true)
          }
        }
        span(1) {
          operationName "play.request"
          resourceName status == 404 ? "404" : "GET $route"
          spanType DDSpanTypes.HTTP_SERVER
          childOf(span(0))
          errored isError
          tags {
            "http.status_code" status
            "http.url" "http://localhost:$port/$path"
            "http.method" "GET"
            "peer.ipv4" "127.0.0.1"
            "span.kind" "server"
            "component" "play-action"
            if (isError) {
              if (exception) {
                errorTags(exception.class, exception.message)
              } else {
                "error" true
              }
            }
            defaultTags()
          }
        }
        if (extraSpans) {
          span(2) {
            operationName "TracedWork\$.doWork"
            childOf(span(1))
            tags {
              "component" "trace"
              defaultTags()
            }
          }
        }
      }
    }

    where:
    path              | route              | body              | status | isError | exception
    "helloplay/spock" | "/helloplay/:from" | "hello spock"     | 200    | false   | null
    "make-error"      | "/make-error"      | "Really sorry..." | 500    | true    | null
    "exception"       | "/exception"       | String            | 500    | true    | new RuntimeException("oh no")
    "nowhere"         | "/nowhere"         | "Really sorry..." | 404    | false   | null

    extraSpans = !isError && status != 404
  }
}
