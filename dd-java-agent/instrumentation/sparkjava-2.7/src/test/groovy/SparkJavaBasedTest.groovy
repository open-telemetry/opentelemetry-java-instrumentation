import datadog.trace.agent.test.AgentTestRunner
import okhttp3.OkHttpClient
import okhttp3.Request
import spock.lang.Timeout


@Timeout(5)
class SparkJavaBasedTest extends AgentTestRunner {

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

}
