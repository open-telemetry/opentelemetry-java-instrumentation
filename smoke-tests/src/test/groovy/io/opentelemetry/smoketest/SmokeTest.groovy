/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest

import static java.util.stream.Collectors.toSet

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.protobuf.util.JsonFormat
import io.opentelemetry.instrumentation.test.utils.OkHttpUtils
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
import org.testcontainers.containers.wait.strategy.WaitStrategy
import org.testcontainers.images.PullPolicy
import org.testcontainers.utility.MountableFile
import spock.lang.Shared
import spock.lang.Specification

abstract class SmokeTest extends Specification {
  private static final Logger logger = LoggerFactory.getLogger(SmokeTest)
  private static final Pattern TRACE_ID_PATTERN = Pattern.compile(".*traceId=(?<traceId>[a-zA-Z0-9]+).*")

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()

  protected static final OkHttpClient CLIENT = OkHttpUtils.client()

  @Shared
  private Network network = Network.newNetwork()
  @Shared
  protected String agentPath = System.getProperty("io.opentelemetry.smoketest.agent.shadowJar.path")

  @Shared
  protected GenericContainer target

  protected abstract String getTargetImage(String jdk, String serverVersion)

  /**
   * Subclasses can override this method to customise target application's environment
   */
  protected Map<String, String> getExtraEnv() {
    return Collections.emptyMap()
  }

  /**
   * Subclasses can override this method to customise target application's environment
   */
  protected void customizeContainer(GenericContainer container) {
  }

  @Shared
  private GenericContainer backend

  @Shared
  private GenericContainer collector

  def setupSpec() {
    backend = new GenericContainer<>("ghcr.io/open-telemetry/java-test-containers:smoke-fake-backend-20201128.1734635")
      .withExposedPorts(8080)
      .waitingFor(Wait.forHttp("/health").forPort(8080))
      .withNetwork(network)
      .withNetworkAliases("backend")
      .withImagePullPolicy(PullPolicy.alwaysPull())
      .withLogConsumer(new Slf4jLogConsumer(logger))
    backend.start()

    collector = new GenericContainer<>("otel/opentelemetry-collector-dev:latest")
      .dependsOn(backend)
      .withNetwork(network)
      .withNetworkAliases("collector")
      .withLogConsumer(new Slf4jLogConsumer(logger))
      .withImagePullPolicy(PullPolicy.alwaysPull())
      .withCopyFileToContainer(MountableFile.forClasspathResource("/otel.yaml"), "/etc/otel.yaml")
      .withCommand("--config /etc/otel.yaml")
    collector.start()
  }

  def startTarget(int jdk, String serverVersion = null) {
    startTarget(String.valueOf(jdk), serverVersion)
  }

  def startTarget(String jdk, String serverVersion = null) {
    def output = new ToStringConsumer()
    target = new GenericContainer<>(getTargetImage(jdk, serverVersion))
      .withExposedPorts(8080)
      .withNetwork(network)
      .withLogConsumer(output)
      .withLogConsumer(new Slf4jLogConsumer(logger))
      .withCopyFileToContainer(MountableFile.forHostPath(agentPath), "/opentelemetry-javaagent-all.jar")
      .withEnv("JAVA_TOOL_OPTIONS", "-javaagent:/opentelemetry-javaagent-all.jar -Dio.opentelemetry.javaagent.slf4j.simpleLogger.log.muzzleMatcher=true")
      .withEnv("OTEL_BSP_MAX_EXPORT_BATCH_SIZE", "1")
      .withEnv("OTEL_BSP_SCHEDULE_DELAY_MILLIS", "10")
      .withEnv("OTEL_EXPORTER_OTLP_ENDPOINT", "http://collector:55680")
      .withImagePullPolicy(PullPolicy.alwaysPull())
      .withEnv(extraEnv)
    customizeContainer(target)

    WaitStrategy waitStrategy = getWaitStrategy()
    if (waitStrategy != null) {
      target = target.waitingFor(waitStrategy)
    }

    target.start()
    output
  }

  protected WaitStrategy getWaitStrategy() {
    return null
  }

  def cleanup() {
    CLIENT.newCall(new Request.Builder()
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
    network.close()
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
      def body = content = CLIENT.newCall(new Request.Builder()
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

  protected static boolean isVersionLogged(ToStringConsumer output, String version) {
    output.toUtf8String().lines()
      .filter({ it.contains("opentelemetry-javaagent - version: " + version) })
      .findFirst()
      .isPresent()
  }
}
