import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.api.DDSpanTypes
import datadog.trace.api.DDTags
import io.opentracing.tag.Tags
import okhttp3.OkHttpClient
import okhttp3.Request
import spock.lang.Shared

import static datadog.trace.agent.test.ListWriterAssert.assertTraces

class AkkaHttpServerInstrumentationTest extends AgentTestRunner {
  static {
    System.setProperty("dd.integration.akka-http-server.enabled", "true")
  }

  @Shared
  int asyncPort
  @Shared
  int syncPort

  def setupSpec() {
    AkkaHttpTestAsyncWebServer.start()
    asyncPort = AkkaHttpTestAsyncWebServer.port()
    AkkaHttpTestSyncWebServer.start()
    syncPort = AkkaHttpTestSyncWebServer.port()
  }

  def cleanupSpec() {
    AkkaHttpTestAsyncWebServer.stop()
    AkkaHttpTestSyncWebServer.stop()
  }

  def "#server 200 request trace" () {
    setup:
    OkHttpClient client = new OkHttpClient.Builder().build()
    def request = new Request.Builder()
      .url("http://localhost:$port/test")
      .header("x-datadog-trace-id", "123")
      .header("x-datadog-parent-id", "456")
      .get()
      .build()
    def response = client.newCall(request).execute()

    expect:
    response.code() == 200

    assertTraces(TEST_WRITER, 1) {
      trace(0, 2) {
        span(0) {
          traceId 123
          parentId 456
          serviceName "unnamed-java-app"
          operationName "akka-http.request"
          resourceName "GET /test"
          errored false
          tags {
            defaultTags()
            "$Tags.HTTP_STATUS.key" 200
            "$Tags.HTTP_URL.key" "http://localhost:$port/test"
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
            "$DDTags.SPAN_TYPE" DDSpanTypes.WEB_SERVLET
            "$Tags.COMPONENT.key" "akka-http-server"
          }
        }
        span(1) {
          childOf span(0)
          assert span(1).operationName.endsWith('.tracedMethod')
        }
      }
    }

    where:
    server     | port
    "async"    | asyncPort
    "sync"     | syncPort
  }

  def "#server exceptions trace for #endpoint" () {
    setup:
    OkHttpClient client = new OkHttpClient.Builder().build()
    def request = new Request.Builder()
      .url("http://localhost:$port/$endpoint")
      .get()
      .build()
    def response = client.newCall(request).execute()

    expect:
    response.code() == 500

    assertTraces(TEST_WRITER, 1) {
      trace(0, 1) {
        span(0) {
          serviceName "unnamed-java-app"
          operationName "akka-http.request"
          resourceName "GET /$endpoint"
          errored true
          tags {
            defaultTags()
            "$Tags.HTTP_STATUS.key" 500
            "$Tags.HTTP_URL.key" "http://localhost:$port/$endpoint"
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
            "$DDTags.SPAN_TYPE" DDSpanTypes.WEB_SERVLET
            "$Tags.COMPONENT.key" "akka-http-server"
            errorTags RuntimeException, errorMessage
          }
        }
      }
    }

    where:
    server     | port       | endpoint         | errorMessage
    "async"    | asyncPort  | "throw-handler"  | "Oh no handler"
    "async"    | asyncPort  | "throw-callback" | "Oh no callback"
    "sync"     | syncPort   | "throw-handler"  | "Oh no handler"
  }

  def "#server 5xx trace" () {
    setup:
    OkHttpClient client = new OkHttpClient.Builder().build()
    def request = new Request.Builder()
      .url("http://localhost:$port/server-error")
      .get()
      .build()
    def response = client.newCall(request).execute()

    expect:
    response.code() == 500

    assertTraces(TEST_WRITER, 1) {
      trace(0, 1) {
        span(0) {
          serviceName "unnamed-java-app"
          operationName "akka-http.request"
          resourceName "GET /server-error"
          errored true
          tags {
            defaultTags()
            "$Tags.HTTP_STATUS.key" 500
            "$Tags.HTTP_URL.key" "http://localhost:$port/server-error"
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
            "$DDTags.SPAN_TYPE" DDSpanTypes.WEB_SERVLET
            "$Tags.COMPONENT.key" "akka-http-server"
            "$Tags.ERROR.key" true
          }
        }
      }
    }

    where:
    server     | port
    "async"    | asyncPort
    "sync"     | syncPort
  }

  def "#server 4xx trace" () {
    setup:
    OkHttpClient client = new OkHttpClient.Builder().build()
    def request = new Request.Builder()
      .url("http://localhost:$port/not-found")
      .get()
      .build()
    def response = client.newCall(request).execute()

    expect:
    response.code() == 404

    assertTraces(TEST_WRITER, 1) {
      trace(0, 1) {
        span(0) {
          serviceName "unnamed-java-app"
          operationName "akka-http.request"
          resourceName "404"
          errored false
          tags {
            defaultTags()
            "$Tags.HTTP_STATUS.key" 404
            "$Tags.HTTP_URL.key" "http://localhost:$port/not-found"
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
            "$DDTags.SPAN_TYPE" DDSpanTypes.WEB_SERVLET
            "$Tags.COMPONENT.key" "akka-http-server"
          }
        }
      }
    }

    where:
    server     | port
    "async"    | asyncPort
    "sync"     | syncPort
  }
}
