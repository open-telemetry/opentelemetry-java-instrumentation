/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;

import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.smoketest.windows.WindowsTestContainerManager;
import io.opentelemetry.testing.internal.armeria.client.WebClient;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.output.OutputFrame;

public abstract class JavaSmokeTest {
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

  public void withTarget(int jdk, TargetRunner runner) throws Exception {
    startTarget(jdk);
    try {
      runner.runInTarget();
    } finally {
      stopTarget();
    }
  }

  public Consumer<OutputFrame> startTarget(int jdk) {
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

  public void stopTarget() {
    containerManager.stopTarget();
    cleanup();
  }

  protected List<SpanData> waitForTraces() {
    return telemetryRetriever.waitForTraces();
  }

  protected Collection<MetricData> waitForMetrics() {
    return telemetryRetriever.waitForMetrics();
  }

  protected Collection<LogRecordData> waitForLogs() {
    return telemetryRetriever.waitForLogs();
  }

  private static TestContainerManager createContainerManager() {
    return TestContainerManager.useWindowsContainers()
        ? new WindowsTestContainerManager()
        : new LinuxTestContainerManager();
  }
}
