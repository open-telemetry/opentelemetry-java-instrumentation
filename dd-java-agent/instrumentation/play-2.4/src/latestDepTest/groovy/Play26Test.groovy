import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.TestUtils
import okhttp3.OkHttpClient
import okhttp3.Request
import play.api.test.TestServer
import play.test.Helpers
import spock.lang.Shared

import static datadog.trace.agent.test.ListWriterAssert.assertTraces

class Play26Test extends AgentTestRunner {
  static {
    System.setProperty("dd.integration.akka-http-server.enabled", "true")
  }

  @Shared
  int port = TestUtils.randomOpenPort()
  @Shared
  TestServer testServer

  def setupSpec() {
    testServer = Helpers.testServer(port, Play26TestUtils.buildTestApp())
    testServer.start()
  }

  def cleanupSpec() {
    testServer.stop()
  }

  def "request traces" () {
    setup:
    OkHttpClient client = new OkHttpClient.Builder().build()
    def request = new Request.Builder()
      .url("http://localhost:$port/helloplay/spock")
      .header("x-datadog-trace-id", "123")
      .header("x-datadog-parent-id", "456")
      .get()
      .build()
    def response = client.newCall(request).execute()

    expect:
    response.code() == 200
    response.body().string() == "hello spock"
    assertTraces(TEST_WRITER, 1) {
      trace(0, 3) {
        span(0) {
          traceId 123
          parentId 456
          serviceName "unnamed-java-app"
          operationName "akka-http.request"
          resourceName "GET /helloplay/:from"
          errored false
          tags {
            defaultTags()
            "http.status_code" 200
            "http.url" "http://localhost:$port/helloplay/spock"
            "http.method" "GET"
            "span.kind" "server"
            "span.type" "web"
            "component" "akka-http-server"
          }
        }
        span(1) {
          childOf span(0)
          operationName "play.request"
          resourceName "GET /helloplay/:from"
          tags {
            defaultTags()
            "http.status_code" 200
            "http.url" "/helloplay/:from"
            "http.method" "GET"
            "span.kind" "server"
            "span.type" "web"
            "component" "play-action"
          }
        }
        span(2) {
          childOf span(1)
          operationName 'TracedWork$.doWork'
        }
      }
    }
  }

  def "5xx errors trace" () {
    setup:
    OkHttpClient client = new OkHttpClient.Builder().build()
    def request = new Request.Builder()
      .url("http://localhost:$port/make-error")
      .get()
      .build()
    def response = client.newCall(request).execute()

    expect:
    response.code() == 500
    assertTraces(TEST_WRITER, 1) {
      trace(0, 2) {
        span(0) {
          serviceName "unnamed-java-app"
          operationName "akka-http.request"
          resourceName "GET /make-error"
          errored true
          tags {
            defaultTags()
            "http.status_code" 500
            "http.url" "http://localhost:$port/make-error"
            "http.method" "GET"
            "span.kind" "server"
            "span.type" "web"
            "component" "akka-http-server"
            "error" true
          }
        }
        span(1) {
          childOf span(0)
          operationName "play.request"
          resourceName "GET /make-error"
          errored true
          tags {
            defaultTags()
            "http.status_code" 500
            "http.url" "/make-error"
            "http.method" "GET"
            "span.kind" "server"
            "span.type" "web"
            "component" "play-action"
            "error" true
          }
        }
      }
    }
  }

  def "error thrown in request" () {
    setup:
    OkHttpClient client = new OkHttpClient.Builder().build()
    def request = new Request.Builder()
      .url("http://localhost:$port/exception")
      .get()
      .build()
    def response = client.newCall(request).execute()

    expect:
    testServer != null
    response.code() == 500
    assertTraces(TEST_WRITER, 1) {
      trace(0, 2) {
        span(0) {
          serviceName "unnamed-java-app"
          operationName "akka-http.request"
          resourceName "GET /exception"
          errored true
          tags {
            defaultTags()
            "http.status_code" 500
            "http.url" "http://localhost:$port/exception"
            "http.method" "GET"
            "span.kind" "server"
            "span.type" "web"
            "component" "akka-http-server"
            "error" true
          }
        }
        span(1) {
          childOf span(0)
          operationName "play.request"
          resourceName "GET /exception"
          errored true
          tags {
            defaultTags()
            "http.status_code" 500
            "http.url" "/exception"
            "http.method" "GET"
            "span.kind" "server"
            "span.type" "web"
            "component" "play-action"
            "error" true
            "error.msg" "oh no"
            "error.type" RuntimeException.getName()
            "error.stack" String
          }
        }
      }
    }
  }

  def "4xx errors trace" () {
    setup:
    OkHttpClient client = new OkHttpClient.Builder().build()
    def request = new Request.Builder()
      .url("http://localhost:$port/nowhere")
      .get()
      .build()
    def response = client.newCall(request).execute()

    expect:
    response.code() == 404

    assertTraces(TEST_WRITER, 1) {
      trace(0, 2) {
        span(0) {
          serviceName "unnamed-java-app"
          operationName "akka-http.request"
          resourceName "404"
          errored false
          tags {
            defaultTags()
            "http.status_code" 404
            "http.url" "http://localhost:$port/nowhere"
            "http.method" "GET"
            "span.kind" "server"
            "span.type" "web"
            "component" "akka-http-server"
          }
        }
        span(1) {
          childOf span(0)
          operationName "play.request"
          resourceName "404"
          errored false
          tags {
            defaultTags()
            "http.status_code" 404
            "http.method" "GET"
            "span.kind" "server"
            "span.type" "web"
            "component" "play-action"
          }
        }
      }
    }
  }
}
