/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.smoketest.windows.WindowsTestContainerManager;
import io.opentelemetry.testing.internal.armeria.client.WebClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class JavaSmokeTest implements TelemetryRetrieverProvider {

  @RegisterExtension
  public static final InstrumentationExtension testing = SmokeTestInstrumentationExtension.create();

  protected static final TestContainerManager containerManager = createContainerManager();

  private final SmokeTestTarget target;
  private static TelemetryRetriever telemetryRetriever;

  protected String agentPath =
      System.getProperty("io.opentelemetry.smoketest.agent.shadowJar.path");

  @RegisterExtension static final AutoCleanupExtension autoCleanup = AutoCleanupExtension.create();

  public JavaSmokeTest(SmokeTestTarget.Builder builder) {
    this.target = customize(builder).build();
  }

  protected SmokeTestTarget.Builder customize(SmokeTestTarget.Builder builder) {
    return builder;
  }

  public WebClient client() {
    return WebClient.of("h1c://localhost:" + containerManager.getTargetMappedPort(8080));
  }

  @BeforeAll
  static void setUp() {
    containerManager.startEnvironmentOnce();
    telemetryRetriever = new TelemetryRetriever(containerManager.getBackendMappedPort());
  }

  protected SmokeTestOutput startTarget(int jdk) {
    return startTarget(String.valueOf(jdk), null, false);
  }

  protected SmokeTestOutput startTarget(String jdk, String serverVersion, boolean windows) {
    String targetImage = target.getTargetImage(jdk, serverVersion, windows);
    autoCleanup.deferCleanup(() -> containerManager.stopTarget());

    return new SmokeTestOutput(
        containerManager.startTarget(
            targetImage,
            agentPath,
            target.getJvmArgsEnvVarName(),
            target.getExtraEnv(),
            target.getSetServiceName(),
            target.getExtraResources(),
            target.getExtraPorts(),
            target.getWaitStrategy(),
            target.getCommand()));
  }

  private static TestContainerManager createContainerManager() {
    return TestContainerManager.useWindowsContainers()
        ? new WindowsTestContainerManager()
        : new LinuxTestContainerManager();
  }

  @Override
  public TelemetryRetriever getTelemetryRetriever() {
    return telemetryRetriever;
  }
}
