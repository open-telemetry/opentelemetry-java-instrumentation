import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.api.DDSpanTypes
import okhttp3.OkHttpClient
import okhttp3.Request
import spock.lang.Timeout


@Timeout(20)
class SparkJavaBasedTest extends AgentTestRunner {

  static {
    TestSparkJavaApplication.initSpark()
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
    def span = trace[0]

    span.context().operationName == "jetty.request"
    span.context().resourceName == "GET /param/:param/"
    span.context().spanType == DDSpanTypes.WEB_SERVLET
    !span.context().getErrorFlag()
    span.context().parentId == 0
    span.context().tags["http.url"] == "http://localhost:$port/param/asdf1234/"
    span.context().tags["http.method"] == "GET"
    span.context().tags["span.kind"] == "server"
    span.context().tags["span.type"] == "web"
    span.context().tags["component"] == "java-web-servlet"
    span.context().tags["http.status_code"] == 200
    span.context().tags["thread.name"] != null
    span.context().tags["thread.id"] != null
    span.context().tags.size() == 8
  }

}
