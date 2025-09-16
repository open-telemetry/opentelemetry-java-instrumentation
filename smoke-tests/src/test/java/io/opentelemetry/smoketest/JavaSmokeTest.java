/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toSet;

import io.opentelemetry.instrumentation.testing.junit.MetricsAssert;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.testing.assertj.MetricAssert;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.smoketest.windows.WindowsTestContainerManager;
import io.opentelemetry.testing.internal.armeria.client.WebClient;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.output.ToStringConsumer;

public abstract class JavaSmokeTest {
  private static final Pattern TRACE_ID_PATTERN =
      Pattern.compile(".*trace_id=(?<traceId>[a-zA-Z0-9]+).*");

  protected static final TestContainerManager containerManager = createContainerManager();
  private JavaTelemetryRetriever telemetryRetriever;

  protected String agentPath =
      System.getProperty("io.opentelemetry.smoketest.agent.shadowJar.path");

  protected WebClient client() {
    return WebClient.of("h1c://localhost:" + containerManager.getTargetMappedPort(8080));
  }

  /** Subclasses can override this method to pass jvm arguments in another environment variable */
  protected String getJvmArgsEnvVarName() {
    return "JAVA_TOOL_OPTIONS";
  }

  /** Subclasses can override this method to customise target application's environment */
  protected Map<String, String> getExtraEnv() {
    return emptyMap();
  }

  /** Subclasses can override this method to disable setting default service name */
  protected boolean getSetServiceName() {
    return true;
  }

  /** Subclasses can override this method to provide additional files to copy to target container */
  protected List<ResourceMapping> getExtraResources() {
    return emptyList();
  }

  /**
   * Subclasses can override this method to provide additional ports that should be exposed from the
   * target container
   */
  protected List<Integer> getExtraPorts() {
    return emptyList();
  }

  @BeforeEach
  void setUp() {
    containerManager.startEnvironmentOnce();
    telemetryRetriever = new JavaTelemetryRetriever(containerManager.getBackendMappedPort());
  }

  public void runTarget(int jdk, TargetRunner runner) throws Exception {
    Consumer<OutputFrame> startTarget = startTarget(jdk);
    try {
      runner.runInTarget(startTarget);
    } finally {
      stopTarget();
    }
  }

  private Consumer<OutputFrame> startTarget(int jdk) {
    return startTarget(String.valueOf(jdk), null, false);
  }

  public Consumer<OutputFrame> startTarget(String jdk, String serverVersion, boolean windows) {
    String targetImage = getTargetImage(jdk, serverVersion, windows);
    return containerManager.startTarget(
        targetImage,
        agentPath,
        getJvmArgsEnvVarName(),
        getExtraEnv(),
        getSetServiceName(),
        getExtraResources(),
        getExtraPorts(),
        getWaitStrategy(),
        getCommand());
  }

  protected abstract String getTargetImage(String jdk);

  protected String getTargetImage(String jdk, String serverVersion, boolean windows) {
    return getTargetImage(jdk);
  }

  protected TargetWaitStrategy getWaitStrategy() {
    return null;
  }

  protected String[] getCommand() {
    return null;
  }

  public void cleanup() {
    telemetryRetriever.clearTelemetry();
  }

  private void stopTarget() {
    containerManager.stopTarget();
    cleanup();
  }

  protected List<SpanData> waitForTraces() {
    return telemetryRetriever.waitForTraces();
  }

  @SafeVarargs
  @SuppressWarnings("varargs")
  protected final void waitAndAssertMetrics(
      String instrumentationName, Consumer<MetricAssert>... assertions) {
    MetricsAssert.waitAndAssertMetrics(
        () -> telemetryRetriever.waitForMetrics(), instrumentationName, assertions);
  }

  protected Collection<LogRecordData> waitForLogs() {
    return telemetryRetriever.waitForLogs();
  }

  protected static boolean isVersionLogged(Consumer<OutputFrame> output, String version) {
    return logLines(output)
        .anyMatch(l -> l.contains("opentelemetry-javaagent - version: " + version));
  }

  private static Stream<String> logLines(Consumer<OutputFrame> output) {
    return ((ToStringConsumer) output).toUtf8String().lines();
  }

  protected static Set<String> getLoggedTraceIds(Consumer<OutputFrame> output) {
    return logLines(output).flatMap(l -> findTraceId(l)).collect(toSet());
  }

  private static Stream<String> findTraceId(String log) {
    var m = TRACE_ID_PATTERN.matcher(log);
    return m.matches() ? Stream.of(m.group("traceId")) : Stream.empty();
  }

  private static TestContainerManager createContainerManager() {
    return TestContainerManager.useWindowsContainers()
        ? new WindowsTestContainerManager()
        : new LinuxTestContainerManager();
  }
}
