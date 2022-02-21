/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest

import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceRequest
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest
import io.opentelemetry.proto.common.v1.AnyValue
import io.opentelemetry.proto.trace.v1.Span
import io.opentelemetry.smoketest.windows.WindowsTestContainerManager
import io.opentelemetry.testing.internal.armeria.client.WebClient
import org.testcontainers.containers.output.ToStringConsumer
import spock.lang.Shared
import spock.lang.Specification

import java.util.regex.Pattern
import java.util.stream.Stream

import static io.opentelemetry.smoketest.TestContainerManager.useWindowsContainers
import static java.util.stream.Collectors.toSet

abstract class SmokeTest extends Specification {
  private static final Pattern TRACE_ID_PATTERN = Pattern.compile(".*trace_id=(?<traceId>[a-zA-Z0-9]+).*")

  protected static final TestContainerManager containerManager = createContainerManager()

  @Shared
  private TelemetryRetriever telemetryRetriever

  @Shared
  protected String agentPath = System.getProperty("io.opentelemetry.smoketest.agent.shadowJar.path")

  protected WebClient client() {
    return WebClient.of("h1c://localhost:${containerManager.getTargetMappedPort(8080)}")
  }

  /**
   * Subclasses can override this method to pass jvm arguments in another environment variable
   */
  protected String getJvmArgsEnvVarName() {
    return "JAVA_TOOL_OPTIONS"
  }

  /**
   * Subclasses can override this method to customise target application's environment
   */
  protected Map<String, String> getExtraEnv() {
    return Collections.emptyMap()
  }

  /**
   * Subclasses can override this method to provide additional files to copy to target container
   */
  protected List<ResourceMapping> getExtraResources() {
    return []
  }

  /**
   * Subclasses can override this method to provide additional ports that should be exposed from the
   * target container
   */
  protected List<ResourceMapping> getExtraPorts() {
    return []
  }

  def setupSpec() {
    containerManager.startEnvironmentOnce()
    telemetryRetriever = new TelemetryRetriever(containerManager.getBackendMappedPort())
  }

  def startTarget(int jdk) {
    startTarget(String.valueOf(jdk), null, false)
  }

  def startTarget(String jdk, String serverVersion, boolean windows) {
    def targetImage = getTargetImage(jdk, serverVersion, windows)
    return containerManager.startTarget(targetImage, agentPath, jvmArgsEnvVarName, extraEnv, extraResources, extraPorts, getWaitStrategy(), getCommand())
  }

  protected abstract String getTargetImage(String jdk)

  protected String getTargetImage(String jdk, String serverVersion, boolean windows) {
    return getTargetImage(jdk)
  }

  protected TargetWaitStrategy getWaitStrategy() {
    return null
  }

  protected String[] getCommand() {
    return null
  }

  def cleanup() {
    telemetryRetriever.clearTelemetry()
  }

  def stopTarget() {
    containerManager.stopTarget()
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
    return telemetryRetriever.waitForTraces()
  }

  protected Collection<ExportMetricsServiceRequest> waitForMetrics() {
    return telemetryRetriever.waitForMetrics()
  }

  protected Collection<ExportLogsServiceRequest> waitForLogs() {
    return telemetryRetriever.waitForLogs()
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

  private static TestContainerManager createContainerManager() {
    return useWindowsContainers() ? new WindowsTestContainerManager() : new LinuxTestContainerManager()
  }
}
