package io.opentelemetry.smoketest

import okhttp3.Request

class SpringBootSmokeTest extends AbstractServerSmokeTest {

  def static final HANDLER_SPAN = "LOGGED_SPAN spring.handler"
  def static final SERVLET_SPAN = "LOGGED_SPAN servlet.request"

  @Override
  ProcessBuilder createProcessBuilder() {
    String springBootShadowJar = System.getProperty("io.opentelemetry.smoketest.springboot.shadowJar.path")

    List<String> command = new ArrayList<>()
    command.add(javaPath())
    command.addAll(defaultJavaProperties)
    command.addAll((String[]) ["-Dota.exporter.jar=${exporterPath}", "-Dota.exporter.prefix=LOGGED_SPAN", "-jar", springBootShadowJar, "--server.port=${httpPort}"])
    ProcessBuilder processBuilder = new ProcessBuilder(command)
    processBuilder.directory(new File(buildDirectory))
  }

  def "default home page #n th time"() {
    setup:
    def spanCounter = new SpanCounter([
      (HANDLER_SPAN): 1,
      (SERVLET_SPAN): 1,
    ], 10000)
    String url = "http://localhost:${httpPort}/greeting"
    def request = new Request.Builder().url(url).get().build()

    when:
    spanCounter.run(logfile)
    def response = client.newCall(request).execute()
    def spans = spanCounter.waitForResult()

    then:
    def responseBodyStr = response.body().string()
    responseBodyStr != null
    responseBodyStr.contains("Sup Dawg")
    response.body().contentType().toString().contains("text/plain")
    response.code() == 200
    spans[HANDLER_SPAN] == 1
    spans[SERVLET_SPAN] == 1

    where:
    n << (1..200)
  }
}
