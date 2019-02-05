import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.api.Config
import datadog.trace.api.DDSpanTypes
import io.opentracing.tag.Tags
import okhttp3.OkHttpClient
import okhttp3.Request

import static datadog.trace.agent.test.server.http.TestHttpServer.httpServer
import static datadog.trace.agent.test.utils.TraceUtils.withConfigOverride

class OkHttp3Test extends AgentTestRunner {

  def "sending a request creates spans and sends headers"() {
    setup:
    def server = httpServer {
      handlers {
        all {
          response.status(200).send("pong")
        }
      }
    }
    def client = new OkHttpClient()
    def request = new Request.Builder()
      .url("http://localhost:$server.address.port/ping")
      .build()

    def response = withConfigOverride("dd.$Config.HTTP_CLIENT_HOST_SPLIT_BY_DOMAIN", "$renameService") {
      client.newCall(request).execute()
    }

    expect:
    response.body.string() == "pong"
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          operationName "okhttp.http"
          serviceName "okhttp"
          resourceName "okhttp.http"
          spanType DDSpanTypes.HTTP_CLIENT
          errored false
          parent()
          tags {
            "component" "okhttp"
            "span.type" DDSpanTypes.HTTP_CLIENT
            defaultTags()
          }
        }
        span(1) {
          operationName "okhttp.http"
          serviceName renameService ? "localhost" : "okhttp"
          resourceName "GET /ping"
          errored false
          childOf(span(0))
          tags {
            defaultTags()
            "component" "okhttp"
            "span.type" DDSpanTypes.HTTP_CLIENT
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" 200
            "$Tags.HTTP_URL.key" "http://localhost:$server.address.port/ping"
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_PORT.key" server.address.port
            "$Tags.PEER_HOST_IPV4.key" "127.0.0.1"
          }
        }
      }
    }

    server.lastRequest.headers.get("x-datadog-trace-id") == TEST_WRITER[0][1].traceId
    server.lastRequest.headers.get("x-datadog-parent-id") == TEST_WRITER[0][1].spanId

    cleanup:
    server.close()

    where:
    renameService << [false, true]
  }
}
