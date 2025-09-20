/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.smoketest.windows.WindowsTestContainerManager;
import io.opentelemetry.testing.internal.armeria.client.WebClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class SmokeTestTarget
    implements BeforeAllCallback,
        BeforeEachCallback,
        AfterAllCallback,
        AfterEachCallback,
        TelemetryRetrieverProvider {

  private final TestContainerManager containerManager = createContainerManager();

  private final InstrumentationExtension testing = new SmokeTestInstrumentationExtension(this);

  private TelemetryRetriever telemetryRetriever;

  private final String agentPath =
      System.getProperty("io.opentelemetry.smoketest.agent.shadowJar.path");

  private final AutoCleanupExtension autoCleanup = AutoCleanupExtension.create();

  public WebClient client() {
    return WebClient.of("h1c://localhost:" + containerManager.getTargetMappedPort(8080));
  }

  @Override
  public void beforeAll(ExtensionContext context) throws Exception {
    containerManager.startEnvironmentOnce();
    telemetryRetriever = new TelemetryRetriever(containerManager.getBackendMappedPort());

    testing.beforeAll(context);
  }

  @Override
  public void beforeEach(ExtensionContext context) {
    testing.beforeEach(context);
  }

  @Override
  public void afterEach(ExtensionContext context) throws Exception {
    autoCleanup.afterEach(context);
    testing.afterEach(context);
  }

  @Override
  public void afterAll(ExtensionContext context) throws Exception {
    testing.afterAll(context);
  }

  public String getAgentPath() {
    return agentPath;
  }

  public InstrumentationExtension testing() {
    return testing;
  }

  public SmokeTestOutput start(int jdk) {
    return start(String.valueOf(jdk), null, false);
  }

  public SmokeTestOutput start(String jdk, String serverVersion, boolean windows) {
    String targetImage = getTargetImage(jdk, serverVersion, windows);
    autoCleanup.deferCleanup(() -> containerManager.stopTarget());

    return new SmokeTestOutput(
        containerManager.startTarget(
            targetImage,
            agentPath,
            getJvmArgsEnvVarName(),
            getExtraEnv(),
            getSetServiceName(),
            getExtraResources(),
            getExtraPorts(),
            getWaitStrategy(),
            getCommand()));
  }

  @Override
  public TelemetryRetriever getTelemetryRetriever() {
    return telemetryRetriever;
  }

  @FunctionalInterface
  public interface GetTargetImage {
    String getTargetImage(String jdk, String serverVersion, boolean windows);
  }

  private final GetTargetImage getTargetImage;

  private final String[] command;
  private final String jvmArgsEnvVarName;
  private final boolean setServiceName;
  private final Map<String, String> extraEnv;
  private final List<ResourceMapping> extraResources;
  private final TargetWaitStrategy waitStrategy;
  private final List<Integer> extraPorts;

  public SmokeTestTarget(
      GetTargetImage getTargetImage,
      String[] command,
      String jvmArgsEnvVarName,
      boolean setServiceName,
      Map<String, String> extraEnv,
      List<ResourceMapping> extraResources,
      TargetWaitStrategy waitStrategy,
      List<Integer> extraPorts) {
    this.getTargetImage = getTargetImage;
    this.command = command;
    this.jvmArgsEnvVarName = jvmArgsEnvVarName;
    this.setServiceName = setServiceName;
    this.extraEnv = extraEnv;
    this.extraResources = extraResources;
    this.waitStrategy = waitStrategy;
    this.extraPorts = extraPorts;
  }

  public String getTargetImage(String jdk, String serverVersion, boolean windows) {
    return getTargetImage.getTargetImage(jdk, serverVersion, windows);
  }

  public String[] getCommand() {
    return command;
  }

  /** Subclasses can override this method to pass jvm arguments in another environment variable */
  public String getJvmArgsEnvVarName() {
    return jvmArgsEnvVarName;
  }

  /** Subclasses can override this method to customise target application's environment */
  public Map<String, String> getExtraEnv() {
    return extraEnv;
  }

  /** Subclasses can override this method to disable setting default service name */
  public boolean getSetServiceName() {
    return setServiceName;
  }

  /** Subclasses can override this method to provide additional files to copy to target container */
  public List<ResourceMapping> getExtraResources() {
    return extraResources;
  }

  /**
   * Subclasses can override this method to provide additional ports that should be exposed from the
   * target container
   */
  public List<Integer> getExtraPorts() {
    return extraPorts;
  }

  public TargetWaitStrategy getWaitStrategy() {
    return waitStrategy;
  }

  public static Builder builder(Function<String, String> getTargetImage) {
    return builder((jdk, serverVersion, windows) -> getTargetImage.apply(jdk));
  }

  public static Builder builder(GetTargetImage getTargetImage) {
    return new Builder(getTargetImage);
  }

  public static Builder springBoot(String imageTag) {
    return builder(
            jdk ->
                String.format(
                    "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-spring-boot:jdk%s-%s",
                    jdk, imageTag))
        .waitStrategy(
            new TargetWaitStrategy.Log(
                Duration.ofMinutes(1), ".*Started SpringbootApplication in.*"));
  }

  private static TestContainerManager createContainerManager() {
    return TestContainerManager.useWindowsContainers()
        ? new WindowsTestContainerManager()
        : new LinuxTestContainerManager();
  }

  public static class Builder {
    private final GetTargetImage getTargetImage;
    private String[] command;
    private String jvmArgsEnvVarName = "JAVA_TOOL_OPTIONS";
    private boolean setServiceName = true;
    private final Map<String, String> extraEnv = new HashMap<>();
    private List<ResourceMapping> extraResources = List.of();
    private TargetWaitStrategy waitStrategy;
    private List<Integer> extraPorts = List.of();

    private Builder(GetTargetImage getTargetImage) {
      this.getTargetImage = getTargetImage;
    }

    @CanIgnoreReturnValue
    public Builder command(String... command) {
      this.command = command;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder jvmArgsEnvVarName(String jvmArgsEnvVarName) {
      this.jvmArgsEnvVarName = jvmArgsEnvVarName;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setServiceName(boolean setServiceName) {
      this.setServiceName = setServiceName;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder env(String key, String value) {
      this.extraEnv.put(key, value);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder extraResources(ResourceMapping... resources) {
      this.extraResources = List.of(resources);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder waitStrategy(@Nullable TargetWaitStrategy waitStrategy) {
      this.waitStrategy = waitStrategy;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder extraPorts(Integer... ports) {
      this.extraPorts = List.of(ports);
      return this;
    }

    public SmokeTestTarget build() {
      return new SmokeTestTarget(
          getTargetImage,
          command,
          jvmArgsEnvVarName,
          setServiceName,
          extraEnv,
          extraResources,
          waitStrategy,
          extraPorts);
    }
  }
}
