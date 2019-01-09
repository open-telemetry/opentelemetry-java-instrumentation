package datadog.trace.agent

import datadog.trace.agent.test.utils.OkHttpUtils
import okhttp3.OkHttpClient
import okhttp3.Request
import spock.lang.Specification

class PlaySmokeTest extends Specification {

  OkHttpClient client = OkHttpUtils.client()
  private int port = Integer.parseInt(System.getProperty("datadog.smoketest.server.port", "8080"))

  def "welcome endpoint #n th time"() {
    setup:
    String url = "http://localhost:$port/welcome?id=$n"
    def request = new Request.Builder().url(url).get().build()

    when:
    def response = client.newCall(request).execute()

    then:
    def responseBodyStr = response.body().string()
    responseBodyStr == "Welcome $n."
    response.code() == 200

    where:
    n << (1..200)
  }
}
