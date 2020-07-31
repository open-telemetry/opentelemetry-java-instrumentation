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

class SpringBootWithSamplingSmokeTest extends AbstractServerSmokeTest {

  static final HANDLER_SPAN = "LOGGED_SPAN WebController.greeting"
  static final SERVLET_SPAN = "LOGGED_SPAN /greeting"
  static final double SAMPLER_PROBABILITY = 0.2
  static final int NUM_TRIES = 1000
  static final int ALLOWED_DEVIATION = 0.1 * NUM_TRIES

  @Override
  ProcessBuilder createProcessBuilder() {
    String springBootShadowJar = System.getProperty("io.opentelemetry.smoketest.springboot.shadowJar.path")

    List<String> command = new ArrayList<>()
    command.add(javaPath())
    command.addAll(defaultJavaProperties)
    command.addAll((String[]) ["-Dotel.exporter.jar=${exporterPath}", "-Dotel.exporter.logging.prefix=LOGGED_SPAN", "-jar", springBootShadowJar, "--server.port=${httpPort}"])
    ProcessBuilder processBuilder = new ProcessBuilder(command)
    processBuilder.environment().put("OTEL_CONFIG_SAMPLER_PROBABILITY", "${SAMPLER_PROBABILITY}")
    processBuilder.directory(new File(buildDirectory))
  }

  def "default home page with probability sampling enabled"() {
    setup:
    // since sampling is enabled, not really expecting to receive NUM_TRIES spans,
    // instead giving it 10 seconds and then checking below how many spans were received
    def spanCounter = new SpanCounter(logfile, [
      (HANDLER_SPAN): NUM_TRIES,
      (SERVLET_SPAN): NUM_TRIES,
    ], 10000)
    String url = "http://localhost:${httpPort}/greeting"
    def request = new Request.Builder().url(url).get().build()

    when:
    for (int i = 1; i<=NUM_TRIES; i++) {
      client.newCall(request).execute()
    }
    def spans = spanCounter.countSpans()

    then:
    Math.abs(spans[HANDLER_SPAN] - (SAMPLER_PROBABILITY * NUM_TRIES)) <= ALLOWED_DEVIATION
    Math.abs(spans[SERVLET_SPAN] - (SAMPLER_PROBABILITY * NUM_TRIES)) <= ALLOWED_DEVIATION
  }
}
