/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.smoketest.windows.WindowsTestContainerManager;
import io.opentelemetry.testing.internal.armeria.client.WebClient;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.output.ToStringConsumer;

public abstract class JavaSmokeTest extends AbstractRemoteTelemetryTest {

  @RegisterExtension
  public static final InstrumentationExtension testing = SmokeTestInstrumentationExtension.create();

  private static final Pattern TRACE_ID_PATTERN =
      Pattern.compile(".*trace_id=(?<traceId>[a-zA-Z0-9]+).*");
  protected static final TestContainerManager containerManager = createContainerManager();
  private RemoteTelemetryRetriever telemetryRetriever;

  protected String agentPath =
      System.getProperty("io.opentelemetry.smoketest.agent.shadowJar.path");

  @RegisterExtension static final AutoCleanupExtension autoCleanup = AutoCleanupExtension.create();

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

  @BeforeAll
  void setUp() {
    containerManager.startEnvironmentOnce();
    telemetryRetriever = new RemoteTelemetryRetriever(containerManager.getBackendMappedPort());
  }

  protected Consumer<OutputFrame> startTarget(int jdk) {
    return startTarget(String.valueOf(jdk), null, false);
  }

  protected Consumer<OutputFrame> startTarget(String jdk, String serverVersion, boolean windows) {
    String targetImage = getTargetImage(jdk, serverVersion, windows);
    autoCleanup.deferCleanup(() -> containerManager.stopTarget());

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

  protected static void assertVersionLogged(Consumer<OutputFrame> output, String version) {
    assertThat(
            logLines(output)
                .anyMatch(l -> l.contains("opentelemetry-javaagent - version: " + version)))
        .isTrue();
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

  @Override
  public void configureTelemetryRetriever(Consumer<RemoteTelemetryRetriever> action) {
    action.accept(telemetryRetriever);
  }
}
