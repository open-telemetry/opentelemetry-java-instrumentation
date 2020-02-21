package io.opentelemetry.smoketest

import okhttp3.Request
import spock.lang.Shared

class PlaySmokeTest extends AbstractServerSmokeTest {

  static final PLAY_SPAN = 'LOGGED_SPAN play.request'
  static final AKKA_SPAN = 'LOGGED_SPAN akka-http.request'

  @Shared
  File playDirectory = new File("${buildDirectory}/stage/playBinary")

  @Override
  ProcessBuilder createProcessBuilder() {
    ProcessBuilder processBuilder =
      new ProcessBuilder("${playDirectory}/bin/playBinary")
    processBuilder.directory(playDirectory)

    processBuilder.environment().put("JAVA_OPTS",
      defaultJavaProperties.join(" ")
        + " -Dota.exporter.jar=${exporterPath}"
        + " -Dota.exporter.dummy.prefix=LOGGED_SPAN"
        + " -Dconfig.file=${workingDirectory}/conf/application.conf -Dhttp.port=${httpPort}"
        + " -Dhttp.address=127.0.0.1")
    return processBuilder
  }

  def "welcome endpoint #n th time"() {
    setup:
    def spanCounter = new SpanCounter(logfile, [
      (PLAY_SPAN): 1,
      (AKKA_SPAN): 1,
    ], 10000)
    String url = "http://localhost:$httpPort/welcome?id=$n"
    def request = new Request.Builder().url(url).get().build()

    when:
    def response = client.newCall(request).execute()
    def spans = spanCounter.countSpans()

    then:
    def responseBodyStr = response.body().string()
    responseBodyStr == "Welcome $n."
    response.code() == 200
    spans[PLAY_SPAN] == 1
    spans[AKKA_SPAN] == 1

    where:
    n << (1..200)
  }
}
