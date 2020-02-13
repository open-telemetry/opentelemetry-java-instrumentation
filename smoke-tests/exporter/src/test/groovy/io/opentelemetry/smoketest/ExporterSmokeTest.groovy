package io.opentelemetry.smoketest

import okhttp3.Request

class ExporterSmokeTest extends AbstractServerSmokeTest {

  def countSpans(prefix) {
    return logfile.text.tokenize('\n').count {
      it.startsWith prefix
    }
  }

  @Override
  ProcessBuilder createProcessBuilder() {
    def springBootShadowJar = System.getProperty("io.opentelemetry.smoketest.exporter.shadowJar.path")

    List<String> command = new ArrayList<>()
    command.add(javaPath())
    command.addAll(defaultJavaProperties)
    command.addAll((String[]) ["-Dota.exporter.jar=${exporterPath}", "-Dota.exporter.prefix=LOGGED_SPAN", "-jar", springBootShadowJar, "--server.port=${httpPort}"])
    ProcessBuilder processBuilder = new ProcessBuilder(command)
    processBuilder.directory(new File(buildDirectory))
  }

  def "default home page #n th time"() {
    setup:
    String url = "http://localhost:${httpPort}/greeting"
    def request = new Request.Builder().url(url).get().build()

    when:
    def response = client.newCall(request).execute()

    then:
    def responseBodyStr = response.body().string()
    responseBodyStr != null
    responseBodyStr.contains("Sup Dawg")
    response.body().contentType().toString().contains("text/plain")
    response.code() == 200
    countSpans("LOGGED_SPAN spring.handler") == n
    countSpans("LOGGED_SPAN servlet.request") == n

    where:
    n << (1..50)
  }
}
