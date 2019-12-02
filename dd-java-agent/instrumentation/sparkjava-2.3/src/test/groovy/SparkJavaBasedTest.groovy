import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.utils.OkHttpUtils
import datadog.trace.agent.test.utils.PortUtils
import datadog.trace.api.DDSpanTypes
import datadog.trace.instrumentation.api.Tags
import okhttp3.OkHttpClient
import okhttp3.Request
import spark.Spark
import spock.lang.Shared

class SparkJavaBasedTest extends AgentTestRunner {

  static {
    System.setProperty("dd.integration.jetty.enabled", "true")
    System.setProperty("dd.integration.sparkjava.enabled", "true")
  }

  @Shared
  int port

  OkHttpClient client = OkHttpUtils.client()

  def setupSpec() {
    port = PortUtils.randomOpenPort()
    TestSparkJavaApplication.initSpark(port)
  }

  def cleanupSpec() {
    Spark.stop()
  }

  def "generates spans"() {
    setup:
    def request = new Request.Builder()
      .url("http://localhost:$port/param/asdf1234")
      .get()
      .build()
    def response = client.newCall(request).execute()

    expect:
    port != 0
    response.body().string() == "Hello asdf1234"

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          serviceName "unnamed-java-app"
          operationName "jetty.request"
          resourceName "GET /param/:param"
          spanType DDSpanTypes.HTTP_SERVER
          errored false
          parent()
          tags {
            "$Tags.COMPONENT" "jetty-handler"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
            "$Tags.PEER_HOSTNAME" "127.0.0.1"
            "$Tags.PEER_HOST_IPV4" "127.0.0.1"
            "$Tags.PEER_PORT" Integer
            "$Tags.HTTP_URL" "http://localhost:$port/param/asdf1234"
            "$Tags.HTTP_METHOD" "GET"
            "$Tags.HTTP_STATUS" 200
            "span.origin.type" spark.embeddedserver.jetty.JettyHandler.name
            defaultTags()
          }
        }
      }
    }
  }

}
