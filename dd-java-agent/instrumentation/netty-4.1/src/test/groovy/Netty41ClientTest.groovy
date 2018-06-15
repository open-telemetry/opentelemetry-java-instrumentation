import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.TestUtils
import datadog.trace.api.DDSpanTypes
import datadog.trace.api.DDTags
import io.opentracing.tag.Tags
import org.asynchttpclient.AsyncHttpClient
import org.eclipse.jetty.server.Handler
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.AbstractHandler
import org.eclipse.jetty.util.MultiMap
import spock.lang.Shared

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

import static datadog.trace.agent.test.ListWriterAssert.assertTraces
import static org.asynchttpclient.Dsl.asyncHttpClient

class Netty41ClientTest extends AgentTestRunner {
  static {
    System.setProperty("dd.integration.netty.enabled", "true")
  }

  static final PORT = TestUtils.randomOpenPort()

  @Shared
  Server server = new Server(PORT)
  @Shared
  AsyncHttpClient asyncHttpClient = asyncHttpClient()
//    DefaultAsyncHttpClientConfig.Builder.newInstance().setRequestTimeout(Integer.MAX_VALUE).build())
  @Shared
  def headers = new MultiMap()


  def setupSpec() {
    Handler handler = [
      handle: { String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response ->
        request.getHeaderNames().each {
          headers.add(it, request.getHeader(it))
        }
        response.setContentType("text/plaincharset=utf-8")
        response.setStatus(HttpServletResponse.SC_OK)
        baseRequest.setHandled(true)
        response.getWriter().println("Hello World")
      }
    ] as AbstractHandler
    server.setHandler(handler)
    server.start()
  }

  def cleanupSpec() {
    server.stop()
  }

  def cleanup() {
    headers.clear()
  }

  def "test server request/response"() {
    setup:
    def responseFuture = asyncHttpClient.prepareGet("http://localhost:$PORT/").execute()
    def response = responseFuture.get()

    expect:
    response.statusCode == 200
    response.responseBody == "Hello World\n"

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
            "$Tags.HTTP_URL.key" "http://localhost:$PORT/"
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
    headers["x-datadog-trace-id"] == "${TEST_WRITER.get(0).get(0).traceId}"
    headers["x-datadog-parent-id"] == "${TEST_WRITER.get(0).get(0).spanId}"
  }
}
