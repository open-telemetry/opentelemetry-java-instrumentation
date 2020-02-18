package io.opentelemetry.smoketest

import io.opentelemetry.auto.test.utils.PortUtils
import okhttp3.Request
import spock.lang.Shared

class WildflySmokeTest extends AbstractServerSmokeTest {

  @Shared
  File wildflyDirectory = new File(System.getProperty("io.opentelemetry.smoketest.wildflyDir"))
  @Shared
  int httpsPort = PortUtils.randomOpenPort()
  @Shared
  int managementPort = PortUtils.randomOpenPort()

  @Override
  ProcessBuilder createProcessBuilder() {
    ProcessBuilder processBuilder =
      new ProcessBuilder("${wildflyDirectory}/bin/standalone.sh")
    processBuilder.directory(wildflyDirectory)

    // We're installing a span exporter to make sure it doesn't blow anything up, but we're not
    // checking the spans, since JBoss seems to redirect stdout to something we don't have (easy) access to.
    processBuilder.environment().put("JAVA_OPTS",
      defaultJavaProperties.join(" ")
        + " -Dota.exporter.jar=${exporterPath}"
        + " -Dota.exporter.dummy.prefix=LOGGED_SPAN"
        + " -Djboss.http.port=${httpPort} -Djboss.https.port=${httpsPort}"
        + " -Djboss.management.http.port=${managementPort}")
    return processBuilder
  }

  def cleanupSpec() {
    ProcessBuilder processBuilder = new ProcessBuilder(
      "${wildflyDirectory}/bin/jboss-cli.sh",
      "--connect",
      "--controller=localhost:${managementPort}",
      "command=:shutdown")
    processBuilder.directory(wildflyDirectory)
    Process process = processBuilder.start()
    process.waitFor()
  }

  def "default home page #n th time"() {
    setup:
    String url = "http://localhost:$httpPort/"
    def request = new Request.Builder().url(url).get().build()

    when:
    def response = client.newCall(request).execute()

    then:
    def responseBodyStr = response.body().string()
    responseBodyStr != null
    responseBodyStr.contains("Your WildFly instance is running.")
    response.body().contentType().toString().contains("text/html")
    response.code() == 200

    where:
    n << (1..200)
  }
}
