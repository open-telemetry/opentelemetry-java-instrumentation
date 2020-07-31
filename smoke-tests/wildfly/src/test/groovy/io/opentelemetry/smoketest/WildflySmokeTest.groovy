/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.smoketest

import io.opentelemetry.auto.test.utils.PortUtils
import okhttp3.Request
import spock.lang.Retry
import spock.lang.Shared

@Retry(delay = 1000)
class WildflySmokeTest extends AbstractServerSmokeTest {

  @Shared
  File wildflyDirectory = new File(System.getProperty("io.opentelemetry.smoketest.wildflyDir"))
  @Shared
  int httpsPort = PortUtils.randomOpenPort()
  @Shared
  int managementPort = PortUtils.randomOpenPort()

  @Override
  ProcessBuilder createProcessBuilder() {
    String ext = System.getProperty("os.name").startsWith("Windows") ? ".bat" : ".sh"
    ProcessBuilder processBuilder =
      new ProcessBuilder("${wildflyDirectory}/bin/standalone" + ext)
    processBuilder.directory(wildflyDirectory)

    // We're installing a span exporter to make sure it doesn't blow anything up, but we're not
    // checking the spans, since JBoss seems to redirect stdout to something we don't have (easy) access to.
    processBuilder.environment().put("JAVA_OPTS",
      defaultJavaProperties.join(" ")
        + " -Dotel.exporter.jar=${exporterPath}"
        + " -Dotel.exporter.logging.prefix=LOGGED_SPAN"
        + " -Djboss.http.port=${httpPort} -Djboss.https.port=${httpsPort}"
        + " -Djboss.management.http.port=${managementPort}")
    return processBuilder
  }

  def cleanupSpec() {
    String ext = System.getProperty("os.name").startsWith("Windows") ? ".bat" : ".sh"
    ProcessBuilder processBuilder = new ProcessBuilder(
      "${wildflyDirectory}/bin/jboss-cli" + ext,
      "--connect",
      "--controller=localhost:${managementPort}",
      "command=:shutdown")
    processBuilder.directory(wildflyDirectory)
    Process process = processBuilder.start()
    process.getOutputStream().close() // otherwise .bat file waits at end with "Press any key to continue . . ."
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
