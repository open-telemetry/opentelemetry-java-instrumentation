import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.api.DDSpanTypes
import io.opentracing.tag.Tags
import okhttp3.OkHttpClient
import okhttp3.Request
import ratpack.http.Headers

import java.util.concurrent.atomic.AtomicReference

import static datadog.trace.agent.test.ListWriterAssert.assertTraces
import static ratpack.groovy.test.embed.GroovyEmbeddedApp.ratpack

class OkHttp3Test extends AgentTestRunner {

  def "sending a request creates spans and sends headers"() {
    setup:
    def receivedHeaders = new AtomicReference<Headers>()
    def server = ratpack {
      handlers {
        all {
          receivedHeaders.set(request.headers)
          response.status(200).send("pong")
        }
      }
    }
    def client = new OkHttpClient()
    def request = new Request.Builder()
      .url("http://localhost:$server.address.port/ping")
      .build()

    def response = client.newCall(request).execute()

    expect:
    response.body.string() == "pong"
    assertTraces(TEST_WRITER, 1) {
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
          serviceName "okhttp"
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

    receivedHeaders.get().get("x-datadog-trace-id") == TEST_WRITER[0][1].traceId
    receivedHeaders.get().get("x-datadog-parent-id") == TEST_WRITER[0][1].spanId

    cleanup:
    server.close()
  }
}
