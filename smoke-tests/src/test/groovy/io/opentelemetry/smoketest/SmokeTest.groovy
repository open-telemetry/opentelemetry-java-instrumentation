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

import com.google.protobuf.util.JsonFormat
import io.opentelemetry.auto.test.utils.OkHttpUtils
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest
import io.opentelemetry.proto.trace.v1.Span
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.util.stream.Stream
import okhttp3.OkHttpClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.output.Slf4jLogConsumer
import spock.lang.Shared
import spock.lang.Specification

abstract class SmokeTest extends Specification {
  private static final Logger logger = LoggerFactory.getLogger(SmokeTest)

  protected static OkHttpClient client = OkHttpUtils.client()

  @Shared
  private Network network = Network.newNetwork()
  @Shared
  protected String agentPath = System.getProperty("io.opentelemetry.smoketest.agent.shadowJar.path")

  @Shared
  protected GenericContainer target = new GenericContainer<>(getTargetImage())
    .withExposedPorts(8080)
    .withNetwork(network)
    .withLogConsumer(new Slf4jLogConsumer(logger))
    .withFileSystemBind(agentPath, "/opentelemetry-javaagent-all.jar")
    .withEnv("JAVA_TOOL_OPTIONS", "-javaagent:/opentelemetry-javaagent-all.jar")
    .withEnv("OTEL_BSP_MAX_EXPORT_BATCH", "1")
    .withEnv("OTEL_BSP_SCHEDULE_DELAY", "10")
    .withEnv("OTEL_OTLP_ENDPOINT", "collector:55680")
    .withEnv(extraEnv)

  protected abstract String getTargetImage()

  /**
   * Subclasses can override this method to customise target application's environment
   */
  protected Map<String, String> getExtraEnv() {
    return Collections.emptyMap()
  }

  @Shared
  protected GenericContainer collector = new GenericContainer<>("otel/opentelemetry-collector-dev")
    .withNetwork(network)
    .withNetworkAliases("collector")
    .withLogConsumer(new Slf4jLogConsumer(logger))
    .withClasspathResourceMapping("/otel.yaml", "/etc/otel.yaml", BindMode.READ_ONLY)
    .withCommand("--config /etc/otel.yaml")

  def setupSpec() {
    collector.start()
    target.start()
  }

  def cleanupSpec() {
    target.stop()
    collector.stop()
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
    def file = new FileInDocker(collector, "/traces.json")
    waitForFileSizeToStabilize(file)

    return file.readLines().collect {
      def builder = ExportTraceServiceRequest.newBuilder()
      JsonFormat.parser().merge(it, builder)
      return builder.build()
    }
  }


  private static void waitForFileSizeToStabilize(FileInDocker file) {
    long previousSize = 0
    long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(30)
    while ((previousSize == 0) || previousSize != file.getSize() && System.currentTimeMillis() < deadline) {
      previousSize = file.getSize()
      println "Curent file size $previousSize"
      TimeUnit.MILLISECONDS.sleep(500)
    }
  }

  private static class FileInDocker {
    private final GenericContainer collector
    private final File localFile
    private final String pathInDocker

    FileInDocker(GenericContainer collector, String pathInDocker) {
      this.pathInDocker = pathInDocker
      this.collector = collector

      this.localFile = Files.createTempFile("traces", ".json").toFile()
      localFile.deleteOnExit()
      println localFile
    }

    long getSize() {
      collector.copyFileFromContainer(pathInDocker, localFile.absolutePath)
      return localFile.size()
    }

    List<String> readLines() {
      collector.copyFileFromContainer(pathInDocker, localFile.absolutePath)
      return localFile.readLines()
    }
  }
}
