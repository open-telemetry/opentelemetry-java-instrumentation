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

import static java.util.stream.Collectors.toSet

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.protobuf.util.JsonFormat
import io.opentelemetry.auto.test.utils.OkHttpUtils
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest
import io.opentelemetry.proto.common.v1.AnyValue
import io.opentelemetry.proto.trace.v1.Span
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import java.util.stream.Stream
import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.output.ToStringConsumer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.MountableFile
import spock.lang.Shared
import spock.lang.Specification

abstract class SmokeTest extends Specification {
  private static final Logger logger = LoggerFactory.getLogger(SmokeTest)
  private static final Pattern TRACE_ID_PATTERN = Pattern.compile(".*traceId=(?<traceId>[a-zA-Z0-9]+).*")

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()

  protected static OkHttpClient client = OkHttpUtils.client()

  @Shared
  private Network network = Network.newNetwork()
  @Shared
  protected String agentPath = System.getProperty("io.opentelemetry.smoketest.agent.shadowJar.path")

  @Shared
  protected GenericContainer target

  protected abstract String getTargetImage(int jdk)

  /**
   * Subclasses can override this method to customise target application's environment
   */
  protected Map<String, String> getExtraEnv() {
    return Collections.emptyMap()
  }

  @Shared
  private GenericContainer backend

  @Shared
  private GenericContainer collector

  def setupSpec() {
    backend = new GenericContainer<>("open-telemetry-docker-dev.bintray.io/java/smoke-fake-backend")
      .withExposedPorts(8080)
      .waitingFor(Wait.forHttp("/health").forPort(8080))
      .withNetwork(network)
      .withNetworkAliases("backend")
      .withLogConsumer(new Slf4jLogConsumer(logger))
    backend.start()

    collector = new GenericContainer<>("otel/opentelemetry-collector-dev")
      .dependsOn(backend)
      .withNetwork(network)
      .withNetworkAliases("collector")
      .withLogConsumer(new Slf4jLogConsumer(logger))
      .withCopyFileToContainer(MountableFile.forClasspathResource("/otel.yaml"), "/etc/otel.yaml")
      .withCommand("--config /etc/otel.yaml")
    collector.start()
  }

  def startTarget(int jdk) {
    def output = new ToStringConsumer()
    target = new GenericContainer<>(getTargetImage(jdk))
      .withExposedPorts(8080)
      .withNetwork(network)
      .withLogConsumer(output)
      .withLogConsumer(new Slf4jLogConsumer(logger))
      .withCopyFileToContainer(MountableFile.forHostPath(agentPath), "/opentelemetry-javaagent-all.jar")
      .withEnv("JAVA_TOOL_OPTIONS", "-javaagent:/opentelemetry-javaagent-all.jar")
      .withEnv("OTEL_BSP_MAX_EXPORT_BATCH_SIZE", "1")
      .withEnv("OTEL_BSP_SCHEDULE_DELAY_MILLIS", "10")
      .withEnv("OTEL_EXPORTER_OTLP_ENDPOINT", "collector:55680")
      .withEnv(extraEnv)
    target.start()
    output
  }

  def cleanup() {
    client.newCall(new Request.Builder()
      .url("http://localhost:${backend.getMappedPort(8080)}/clear-requests")
      .build())
      .execute()
      .close()
  }

  def stopTarget() {
    target.stop()
  }

  def cleanupSpec() {
    backend.stop()
    collector.stop()
  }

  protected static Stream<AnyValue> findResourceAttribute(Collection<ExportTraceServiceRequest> traces,
                                                          String attributeKey) {
    return traces.stream()
      .flatMap { it.getResourceSpansList().stream() }
      .flatMap { it.getResource().getAttributesList().stream() }
      .filter { it.key == attributeKey }
      .map { it.value }
  }

  protected static int countSpansByName(Collection<ExportTraceServiceRequest> traces, String spanName) {
    return getSpanStream(traces).filter { it.name == spanName }.count()
  }

  protected static Stream<Span> getSpanStream(Collection<ExportTraceServiceRequest> traces) {
    return traces.stream()
      .flatMap { it.getResourceSpansList().stream() }
      .flatMap { it.getInstrumentationLibrarySpansList().stream() }
      .flatMap { it.getSpansList().stream() }
  }

  protected Collection<ExportTraceServiceRequest> waitForTraces() {
    def content = waitForContent()

    return OBJECT_MAPPER.readTree(content).collect {
      def builder = ExportTraceServiceRequest.newBuilder()
      // TODO(anuraaga): Register parser into object mapper to avoid de -> re -> deserialize.
      JsonFormat.parser().merge(OBJECT_MAPPER.writeValueAsString(it), builder)
      return builder.build()
    }
  }

  private String waitForContent() {
    long previousSize = 0
    long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(30)
    String content = "[]"
    while (System.currentTimeMillis() < deadline) {
      def body = content = client.newCall(new Request.Builder()
        .url("http://localhost:${backend.getMappedPort(8080)}/get-requests")
        .build())
        .execute()
        .body()
      try {
        content = body.string()
      } finally {
        body.close()
      }
      if (content.length() > 2 && content.length() == previousSize) {
        break
      }
      previousSize = content.length()
      println "Curent content size $previousSize"
      TimeUnit.MILLISECONDS.sleep(500)
    }

    return content
  }

  protected static Set<String> getLoggedTraceIds(ToStringConsumer output) {
    output.toUtf8String().lines()
      .flatMap(SmokeTest.&findTraceId)
      .collect(toSet())
  }

  private static Stream<String> findTraceId(String log) {
    def m = TRACE_ID_PATTERN.matcher(log)
    m.matches() ? Stream.of(m.group("traceId")) : Stream.empty() as Stream<String>
  }
}
