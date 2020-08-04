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

import groovy.json.JsonSlurper
import io.opentelemetry.auto.test.utils.OkHttpUtils
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.output.Slf4jLogConsumer
import spock.lang.Specification

class SmokeTest extends Specification {
  private static final Logger log = LoggerFactory.getLogger(SmokeTest)

  protected static OkHttpClient client = OkHttpUtils.client()

  private Network network = Network.newNetwork()

  GenericContainer target = new GenericContainer<>("smoke-spring:0.0.1-SNAPSHOT")
    .withExposedPorts(8080)
    .withNetwork(network)
    .withLogConsumer(new Slf4jLogConsumer(log))
    .withFileSystemBind("/Users/nsalnikovtarnovski/Documents/workspace/opentelemetry-auto-instr-java/opentelemetry-javaagent/build/libs/opentelemetry-javaagent-0.7.0-SNAPSHOT-all.jar", "/opentelemetry-javaagent-all.jar")
    .withEnv("JAVA_TOOL_OPTIONS", "-javaagent:/opentelemetry-javaagent-all.jar")
    .withEnv("OTEL_BSP_MAX_EXPORT_BATCH", "1")
    .withEnv("OTEL_BSP_SCHEDULE_DELAY", "10")
    .withEnv("OTEL_OTLP_ENDPOINT", "collector:55680")

  GenericContainer collector = new GenericContainer<>("otel/opentelemetry-collector")
    .withNetwork(network)
    .withNetworkAliases("collector")
    .withLogConsumer(new Slf4jLogConsumer(log))
    .withClasspathResourceMapping("/otel.yaml", "/etc/otel.yaml", BindMode.READ_ONLY)
    .withFileSystemBind("./otel", "/otel", BindMode.READ_WRITE)
    .withCommand("--config /etc/otel.yaml")

  def "test"() {
    setup:
    collector.start()
    target.start()
    String url = "http://localhost:${target.getMappedPort(8080)}"
    def request = new Request.Builder().url(url).get().build()

    when:
    def response = client.newCall(request).execute()
    def traces = waitForFile("./otel/traces.json")

    then:
    response.body().string() == "Hello world"
    traces['spans'].size() == 2

    cleanup:
    target.stop()
    collector.stop()
  }

  private Map waitForFile(String path) {
    def file = new File(path)
    println file.absolutePath
    while (file.size() < 1_000) {
      TimeUnit.MILLISECONDS.sleep(100)
    }
    return new JsonSlurper().parse(file)
  }
}
