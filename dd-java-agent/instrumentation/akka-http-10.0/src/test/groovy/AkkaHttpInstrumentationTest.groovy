import datadog.opentracing.DDSpan
import datadog.trace.agent.test.AgentTestRunner
import okhttp3.OkHttpClient
import okhttp3.Request
import spock.lang.Shared

class AkkaHttpInstrumentationTest extends AgentTestRunner {
  @Shared
  int port

  def setupSpec() {
    WebServer.start()
    port = WebServer.port()
  }

  def cleanupSpec() {
    WebServer.stop()
  }

  def "200 request trace" () {
    setup:
    OkHttpClient client = new OkHttpClient.Builder().build()
    def request = new Request.Builder()
      .url("http://localhost:$port/test")
      .header("x-datadog-trace-id", "123")
      .header("x-datadog-parent-id", "456")
      .get()
      .build()
    def response = client.newCall(request).execute()
    TEST_WRITER.waitForTraces(1)
    DDSpan[] akkaTrace = TEST_WRITER.get(0)
    DDSpan root = akkaTrace[0]
    expect:
    response.code() == 200

    TEST_WRITER.size() == 1
    akkaTrace.size() == 2
    akkaTrace[1].operationName == 'WebServer$.tracedMethod'
    akkaTrace[1].parentId == akkaTrace[0].spanId


    root.traceId == 123
    root.parentId == 456
    root.serviceName == "unnamed-java-app"
    root.operationName == "akkahttp.request"
    root.resourceName == "GET /test"
    !root.context().getErrorFlag()
    root.context().tags["http.status_code"] == 200
    root.context().tags["http.url"] == "http://localhost:$port/test"
    root.context().tags["http.method"] == "GET"
    root.context().tags["span.kind"] == "server"
    root.context().tags["component"] == "akkahttp-action"
  }

  def "exceptions trace for #endpoint" () {
    setup:
    OkHttpClient client = new OkHttpClient.Builder().build()
    def request = new Request.Builder()
      .url("http://localhost:$port/$endpoint")
      .get()
      .build()
    def response = client.newCall(request).execute()
    TEST_WRITER.waitForTraces(1)
    DDSpan[] akkaTrace = TEST_WRITER.get(0)
    DDSpan root = akkaTrace[0]

    expect:
    response.code() == 500
    TEST_WRITER.size() == 1

    root.operationName == "akkahttp.request"
    root.resourceName == "GET /$endpoint"

    root.context().getErrorFlag()
    root.context().getTags()["error.type"] == RuntimeException.name
    root.context().getTags()["error.stack"].toString().startsWith("java.lang.RuntimeException: $errorMessage")

    root.context().tags["http.status_code"] == 500
    root.context().tags["http.url"] == "http://localhost:$port/$endpoint"
    root.context().tags["http.method"] == "GET"
    root.context().tags["span.kind"] == "server"
    root.context().tags["component"] == "akkahttp-action"

    where:
    endpoint         | errorMessage
    "throw-handler"  | "Oh no handler"
    "throw-callback" | "Oh no callback"
  }

  def "5xx trace" () {
    setup:
    OkHttpClient client = new OkHttpClient.Builder().build()
    def request = new Request.Builder()
      .url("http://localhost:$port/server-error")
      .get()
      .build()
    def response = client.newCall(request).execute()
    TEST_WRITER.waitForTraces(1)
    DDSpan[] akkaTrace = TEST_WRITER.get(0)
    DDSpan root = akkaTrace[0]
    expect:
    response.code() == 500

    TEST_WRITER.size() == 1
    akkaTrace.size() == 1

    root.context().getErrorFlag()
    root.context().getTags()["error.stack"] == null

    root.serviceName == "unnamed-java-app"
    root.operationName == "akkahttp.request"
    root.resourceName == "GET /server-error"
    root.context().tags["http.status_code"] == 500
    root.context().tags["http.url"] == "http://localhost:$port/server-error"
    root.context().tags["http.method"] == "GET"
    root.context().tags["span.kind"] == "server"
    root.context().tags["component"] == "akkahttp-action"
  }

  def "4xx trace" () {
    setup:
    OkHttpClient client = new OkHttpClient.Builder().build()
    def request = new Request.Builder()
      .url("http://localhost:$port/not-found")
      .get()
      .build()
    def response = client.newCall(request).execute()
    TEST_WRITER.waitForTraces(1)
    DDSpan[] akkaTrace = TEST_WRITER.get(0)
    DDSpan root = akkaTrace[0]
    expect:
    response.code() == 404

    TEST_WRITER.size() == 1
    akkaTrace.size() == 1

    !root.context().getErrorFlag()
    root.context().getTags()["error.stack"] == null

    root.serviceName == "unnamed-java-app"
    root.operationName == "akkahttp.request"
    root.resourceName == "404"
    root.context().tags["http.status_code"] == 404
    root.context().tags["http.url"] == "http://localhost:$port/not-found"
    root.context().tags["http.method"] == "GET"
    root.context().tags["span.kind"] == "server"
    root.context().tags["component"] == "akkahttp-action"
  }
}

