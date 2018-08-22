import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.api.DDSpanTypes
import datadog.trace.api.DDTags
import io.opentracing.tag.Tags
import org.asynchttpclient.AsyncHttpClient
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import spock.lang.AutoCleanup
import spock.lang.Shared

import java.util.concurrent.TimeUnit

import static datadog.trace.agent.test.asserts.ListWriterAssert.assertTraces
import static datadog.trace.agent.test.server.http.TestHttpServer.httpServer
import static org.asynchttpclient.Dsl.asyncHttpClient

class Netty40ClientTest extends AgentTestRunner {

  @AutoCleanup
  @Shared
  def server = httpServer {
    handlers {
      all {
        response.send("Hello World")
      }
    }
  }
  @Shared
  def clientConfig = DefaultAsyncHttpClientConfig.Builder.newInstance().setRequestTimeout(TimeUnit.MINUTES.toMillis(1).toInteger())
  @Shared
  AsyncHttpClient asyncHttpClient = asyncHttpClient(clientConfig)

  def "test server request/response"() {
    setup:
    def responseFuture = asyncHttpClient.prepareGet("$server.address").execute()
    def response = responseFuture.get()

    expect:
    response.statusCode == 200
    response.responseBody == "Hello World"

    and:
    assertTraces(TEST_WRITER, 1) {
      trace(0, 1) {
        span(0) {
          serviceName "unnamed-java-app"
          operationName "netty.client.request"
          resourceName "GET /"
          spanType DDSpanTypes.HTTP_CLIENT
          errored false
          tags {
            "$Tags.COMPONENT.key" "netty-client"
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" 200
            "$Tags.HTTP_URL.key" "$server.address/"
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_PORT.key" Integer
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_CLIENT
            defaultTags()
          }
        }
      }
    }

    and:
    server.lastRequest.headers.get("x-datadog-trace-id") == "${TEST_WRITER.get(0).get(0).traceId}"
    server.lastRequest.headers.get("x-datadog-parent-id") == "${TEST_WRITER.get(0).get(0).spanId}"
  }
}
