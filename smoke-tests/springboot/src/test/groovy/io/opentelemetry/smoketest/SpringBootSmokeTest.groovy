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

import okhttp3.Request

class SpringBootSmokeTest extends AbstractServerSmokeTest {

  static final HANDLER_SPAN = "LOGGED_SPAN WebController.greeting"
  static final SERVLET_SPAN = "LOGGED_SPAN /greeting"

  @Override
  ProcessBuilder createProcessBuilder() {
    String springBootShadowJar = System.getProperty("io.opentelemetry.smoketest.springboot.shadowJar.path")

    List<String> command = new ArrayList<>()
    command.add(javaPath())
    command.addAll(defaultJavaProperties)
    command.addAll((String[]) ["-Dota.exporter.jar=${exporterPath}", "-Dota.exporter.logging.prefix=LOGGED_SPAN", "-jar", springBootShadowJar, "--server.port=${httpPort}"])
    ProcessBuilder processBuilder = new ProcessBuilder(command)
    processBuilder.directory(new File(buildDirectory))
  }

  def "default home page #n th time"() {
    setup:
    def spanCounter = new SpanCounter(logfile, [
      (HANDLER_SPAN): 1,
      (SERVLET_SPAN): 1,
    ], 30000)
    String url = "http://localhost:${httpPort}/greeting"
    def request = new Request.Builder().url(url).get().build()

    when:
    def response = client.newCall(request).execute()
    def spans = spanCounter.countSpans()

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
