import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.api.DDSpanTypes
import okhttp3.OkHttpClient
import okhttp3.Request
import spark.Spark
import spock.lang.Timeout


@Timeout(20)
class SparkJavaBasedTest extends AgentTestRunner {

  static {
    System.setProperty("dd.integration.jetty.enabled", "true")
    System.setProperty("dd.integration.sparkjava.enabled", "true")
  }

  def setupSpec() {
    TestSparkJavaApplication.initSpark()
  }

  def cleanupSpec() {
    Spark.stop()
  }

  def setup() {
    TEST_WRITER.start()
  }

  private int port = 4567
  OkHttpClient client = new OkHttpClient.Builder().build()

  def "valid response"() {
    setup:
    def request = new Request.Builder()
      .url("http://localhost:$port/")
      .get()
      .build()
    def response = client.newCall(request).execute()

    expect:
    port != 0
    response.body().string() == "Hello World"
  }

  def "valid response with registered trace"() {
    setup:
    def request = new Request.Builder()
      .url("http://localhost:$port/")
      .get()
      .build()
    def response = client.newCall(request).execute()

    expect:
    port != 0
    response.body().string() == "Hello World"

    and:
    TEST_WRITER.waitForTraces(1)
    TEST_WRITER.size() == 1
  }


  def "generates spans"() {
    setup:
    def request = new Request.Builder()
      .url("http://localhost:$port/param/asdf1234")
      .get()
      .build()
    def response = client.newCall(request).execute()

    expect:
    response.body().string() == "Hello asdf1234"
    TEST_WRITER.waitForTraces(1)
    TEST_WRITER.size() == 1

    def trace = TEST_WRITER.firstTrace()
    trace.size() == 1
    def spanContext = trace[0].context()

    spanContext.operationName == "jetty.request"
    spanContext.resourceName == "GET /param/:param/"
    spanContext.spanType == DDSpanTypes.WEB_SERVLET
    !spanContext.getErrorFlag()
    spanContext.parentId == 0
    spanContext.tags["http.url"] == "http://localhost:$port/param/asdf1234/"
    spanContext.tags["http.method"] == "GET"
    spanContext.tags["span.kind"] == "server"
    spanContext.tags["span.type"] == "web"
    spanContext.tags["component"] == "java-web-servlet"
    spanContext.tags["http.status_code"] == 200
    spanContext.tags["thread.name"] != null
    spanContext.tags["thread.id"] != null
    spanContext.tags.size() == 8
  }

}
