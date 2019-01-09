package datadog.trace.agent

import datadog.trace.agent.test.utils.OkHttpUtils
import okhttp3.OkHttpClient
import okhttp3.Request
import spock.lang.Specification

class SpringBootSmokeTest extends Specification {

  private OkHttpClient client = OkHttpUtils.client()
  private int port = Integer.parseInt(System.getProperty("datadog.smoketest.server.port", "8080"))

  def "default home page #n th time"() {
    setup:
    String url = "http://localhost:$port/greeting"
    def request = new Request.Builder().url(url).get().build()

    when:
    def response = client.newCall(request).execute()

    then:
    def responseBodyStr = response.body().string()
    responseBodyStr != null
    responseBodyStr.contains("Sup Dawg")
    response.body().contentType().toString().contains("text/plain")
    response.code() == 200

    where:
    n << (1..200)
  }
}
