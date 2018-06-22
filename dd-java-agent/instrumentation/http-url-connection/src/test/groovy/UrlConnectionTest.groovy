import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.TestUtils
import datadog.trace.api.DDSpanTypes
import datadog.trace.api.DDTags
import io.opentracing.tag.Tags
import io.opentracing.util.GlobalTracer

import static datadog.trace.agent.test.ListWriterAssert.assertTraces
import static datadog.trace.agent.test.TestUtils.runUnderTrace

class UrlConnectionTest extends AgentTestRunner {
  static {
    System.setProperty("dd.integration.httpurlconnection.enabled", "true")
  }

  private static final int INVALID_PORT = TestUtils.randomOpenPort()

  def "trace request with connection failure"() {
    when:
    runUnderTrace("someTrace") {
      URLConnection connection = url.openConnection()
      connection.setConnectTimeout(10000)
      connection.setReadTimeout(10000)
      assert GlobalTracer.get().scopeManager().active() != null
      connection.inputStream
    }

    then:
    thrown ConnectException

    expect:
    assertTraces(TEST_WRITER, 1) {
      trace(0, 2) {
        span(0) {
          operationName "someTrace"
          parent()
          errored true
          tags {
            errorTags ConnectException, "Connection refused (Connection refused)"
            defaultTags()
          }
        }
        span(1) {
          operationName "${scheme}.request.input_stream"
          childOf span(0)
          errored true
          tags {
            "$Tags.COMPONENT.key" component
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_CLIENT
            "$Tags.HTTP_URL.key" "$url"
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_PORT.key" INVALID_PORT
            errorTags ConnectException, "Connection refused (Connection refused)"
            defaultTags()
          }
        }
      }
    }

    where:
    scheme  | component
    "http"  | "HttpURLConnection"
    "https" | "HttpsURLConnectionImpl"

    url = new URI("$scheme://localhost:$INVALID_PORT").toURL()
  }
}
