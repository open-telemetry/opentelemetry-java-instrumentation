/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.smoketest.windows.WindowsTestContainerManager;
import io.opentelemetry.testing.internal.armeria.client.WebClient;
import java.io.IOException;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractSmokeTest<T> implements TelemetryRetrieverProvider {

  @RegisterExtension
  public static final InstrumentationExtension testing = SmokeTestInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension autoCleanup = AutoCleanupExtension.create();

  protected static final TestContainerManager containerManager = createContainerManager();
  private static TelemetryRetriever telemetryRetriever;

  private final String agentPath =
      System.getProperty("io.opentelemetry.smoketest.agent.shadowJar.path");
  private final String agentVersion = readAgentVersion();

  private final SmokeTestOptions<T> options = new SmokeTestOptions<>();

  @BeforeAll
  void setUp() {
    containerManager.startEnvironmentOnce();
    configure(options);
    telemetryRetriever =
        new TelemetryRetriever(containerManager.getBackendMappedPort(), options.telemetryTimeout);
  }

  protected void configure(SmokeTestOptions<T> options) {}

  protected WebClient client() {
    return WebClient.of("h1c://localhost:" + containerManager.getTargetMappedPort(8080));
  }

  public SmokeTestOutput start(T arg) {
    autoCleanup.deferCleanup(this::stop);
    return startWithoutCleanup(arg);
  }

  public void stop() {
    containerManager.stopTarget();
  }

  public SmokeTestOutput startWithoutCleanup(T arg) {
    return new SmokeTestOutput(
        this,
        containerManager.startTarget(
            options.getImage.apply(arg),
            agentPath,
            options.jvmArgsEnvVarName,
            options.extraEnv,
            options.setServiceName,
            options.extraResources,
            options.extraPorts,
            options.waitStrategy,
            options.command));
  }

  private static TestContainerManager createContainerManager() {
    return TestContainerManager.useWindowsContainers()
        ? new WindowsTestContainerManager()
        : new LinuxTestContainerManager();
  }

  public int getMappedPort(int originalPort) {
    return containerManager.getTargetMappedPort(originalPort);
  }

  @Override
  public TelemetryRetriever getTelemetryRetriever() {
    return telemetryRetriever;
  }

  private String readAgentVersion() {
    try (JarFile agentJar = new JarFile(agentPath)) {
      return agentJar
          .getManifest()
          .getMainAttributes()
          .getValue(Attributes.Name.IMPLEMENTATION_VERSION);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  public String getAgentVersion() {
    return agentVersion;
  }

  public Set<String> getSpanTraceIds() {
    return testing.spans().stream().map(SpanData::getTraceId).collect(Collectors.toSet());
  }
}
