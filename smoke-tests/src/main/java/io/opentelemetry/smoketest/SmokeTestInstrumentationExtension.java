/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.smoketest.windows.WindowsTestContainerManager;
import io.opentelemetry.testing.internal.armeria.client.WebClient;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * JUnit 5 extension for writing smoke tests that use a {@link TelemetryRetriever} to retrieve
 * telemetry data from the fake backend.
 *
 * <p>Example usage:
 *
 * <pre>
 *   class MySmokeTest {
 *     {@literal @}RegisterExtension
 *     static final SmokeTestInstrumentationExtension testing = SmokeTestInstrumentationExtension.springBoot("version").build();
 *
 *     {@literal @}ParameterizedTest
 *     {@literal @}ValueSource(ints = {8, 11, 17})
 *     void test(int jdk) throws Exception {
 *     SmokeTestOutput output = testing.start(jdk);
 *       // test code ...
 *
 *       var spans = testing.spans();
 *       // assertions on collected spans ...
 *     }
 *   }
 * </pre>
 */
public class SmokeTestInstrumentationExtension<T> extends InstrumentationExtension
    implements TelemetryRetrieverProvider {

  private final TestContainerManager containerManager = createContainerManager();

  private TelemetryRetriever telemetryRetriever;

  private final String agentPath =
      System.getProperty("io.opentelemetry.smoketest.agent.shadowJar.path");

  private final String agentVersion = readAgentVersion();

  private final AutoCleanupExtension autoCleanup = AutoCleanupExtension.create();

  private final Function<T, String> getTargetImage;
  private final String[] command;
  private final String jvmArgsEnvVarName;
  private final boolean setServiceName;
  private final Map<String, String> extraEnv;
  private final List<ResourceMapping> extraResources;
  private final TargetWaitStrategy waitStrategy;
  private final List<Integer> extraPorts;
  private final Duration telemetryTimeout;

  private SmokeTestInstrumentationExtension(
      Function<T, String> getTargetImage,
      String[] command,
      String jvmArgsEnvVarName,
      boolean setServiceName,
      Map<String, String> extraEnv,
      List<ResourceMapping> extraResources,
      TargetWaitStrategy waitStrategy,
      List<Integer> extraPorts,
      Duration telemetryTimeout) {
    super(new SmokeTestRunner());
    this.getTargetImage = getTargetImage;
    this.command = command;
    this.jvmArgsEnvVarName = jvmArgsEnvVarName;
    this.setServiceName = setServiceName;
    this.extraEnv = extraEnv;
    this.extraResources = extraResources;
    this.waitStrategy = waitStrategy;
    this.extraPorts = extraPorts;
    this.telemetryTimeout = telemetryTimeout;
  }

  public WebClient client() {
    return WebClient.of("h1c://localhost:" + containerManager.getTargetMappedPort(8080));
  }

  @Override
  public void beforeAll(ExtensionContext context) throws Exception {
    containerManager.startEnvironmentOnce();
    telemetryRetriever =
        new TelemetryRetriever(containerManager.getBackendMappedPort(), telemetryTimeout);
    super.beforeAll(context);
  }

  @Override
  public void beforeEach(ExtensionContext extensionContext) {
    SmokeTestRunner smokeTestRunner = (SmokeTestRunner) getTestRunner();
    smokeTestRunner.setTelemetryRetriever(getTelemetryRetriever());
    super.beforeEach(extensionContext);
  }

  @Override
  public void afterEach(ExtensionContext context) throws Exception {
    autoCleanup.afterEach(context);
    super.afterEach(context);
  }

  public String getAgentVersion() {
    return agentVersion;
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

  public int getTargetMappedPort(int originalPort) {
    return containerManager.getTargetMappedPort(originalPort);
  }

  public Set<String> getSpanTraceIds() {
    return spans().stream().map(SpanData::getTraceId).collect(Collectors.toSet());
  }

  public SmokeTestOutput start(T arg) {
    autoCleanup.deferCleanup(() -> containerManager.stopTarget());

    return new SmokeTestOutput(
        this,
        containerManager.startTarget(
            getTargetImage.apply(arg),
            agentPath,
            jvmArgsEnvVarName,
            extraEnv,
            setServiceName,
            extraResources,
            extraPorts,
            waitStrategy,
            command));
  }

  @Override
  public TelemetryRetriever getTelemetryRetriever() {
    return telemetryRetriever;
  }

  public static <T> Builder<T> builder(Function<T, String> getTargetImage) {
    return new Builder<T>(getTargetImage);
  }

  public static Builder<Integer> springBoot(String imageTag) {
    return new Builder<Integer>(
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

  public static class Builder<T> {
    private final Function<T, String> getTargetImage;
    private String[] command;
    private String jvmArgsEnvVarName = "JAVA_TOOL_OPTIONS";
    private boolean setServiceName = true;
    private final Map<String, String> extraEnv = new HashMap<>();
    private List<ResourceMapping> extraResources = List.of();
    private TargetWaitStrategy waitStrategy;
    private List<Integer> extraPorts = List.of();
    private Duration telemetryTimeout = Duration.ofSeconds(30);

    private Builder(Function<T, String> getTargetImage) {
      this.getTargetImage = getTargetImage;
    }

    /** Sets the command to run in the target container. */
    @CanIgnoreReturnValue
    public Builder<T> command(String... command) {
      this.command = command;
      return this;
    }

    /** Sets the environment variable name used to pass JVM arguments to the target application. */
    @CanIgnoreReturnValue
    public Builder<T> jvmArgsEnvVarName(String jvmArgsEnvVarName) {
      this.jvmArgsEnvVarName = jvmArgsEnvVarName;
      return this;
    }

    /** Enables or disables setting the default service name for the target application. */
    @CanIgnoreReturnValue
    public Builder<T> setServiceName(boolean setServiceName) {
      this.setServiceName = setServiceName;
      return this;
    }

    /** Adds an environment variable to the target application's environment. */
    @CanIgnoreReturnValue
    public Builder<T> env(String key, String value) {
      this.extraEnv.put(key, value);
      return this;
    }

    /** Specifies additional files to copy to the target container. */
    @CanIgnoreReturnValue
    public Builder<T> extraResources(ResourceMapping... resources) {
      this.extraResources = List.of(resources);
      return this;
    }

    /** Sets the wait strategy for the target container startup. */
    @CanIgnoreReturnValue
    public Builder<T> waitStrategy(@Nullable TargetWaitStrategy waitStrategy) {
      this.waitStrategy = waitStrategy;
      return this;
    }

    /** Specifies additional ports to expose from the target container. */
    @CanIgnoreReturnValue
    public Builder<T> extraPorts(Integer... ports) {
      this.extraPorts = List.of(ports);
      return this;
    }

    /** Sets the timeout duration for retrieving telemetry data. */
    @CanIgnoreReturnValue
    public Builder<T> telemetryTimeout(Duration telemetryTimeout) {
      this.telemetryTimeout = telemetryTimeout;
      return this;
    }

    public SmokeTestInstrumentationExtension<T> build() {
      return new SmokeTestInstrumentationExtension<T>(
          getTargetImage,
          command,
          jvmArgsEnvVarName,
          setServiceName,
          extraEnv,
          extraResources,
          waitStrategy,
          extraPorts,
          telemetryTimeout);
    }
  }
}
