/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.smoketest.windows.WindowsTestContainerManager;
import io.opentelemetry.testing.internal.armeria.client.WebClient;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.output.ToStringConsumer;

public abstract class JavaSmokeTest implements TelemetryRetrieverProvider {

  @RegisterExtension
  public static final InstrumentationExtension testing = SmokeTestInstrumentationExtension.create();

  private static final Pattern TRACE_ID_PATTERN =
      Pattern.compile(".*trace_id=(?<traceId>[a-zA-Z0-9]+).*");
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

  protected Consumer<OutputFrame> startTarget(int jdk) {
    return startTarget(String.valueOf(jdk), null, false);
  }

  protected Consumer<OutputFrame> startTarget(String jdk, String serverVersion, boolean windows) {
    String targetImage = target.getTargetImage(jdk, serverVersion, windows);
    autoCleanup.deferCleanup(() -> containerManager.stopTarget());

    return containerManager.startTarget(
        targetImage,
        agentPath,
        target.getJvmArgsEnvVarName(),
        target.getExtraEnv(),
        target.getSetServiceName(),
        target.getExtraResources(),
        target.getExtraPorts(),
        target.getWaitStrategy(),
        target.getCommand());
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
    return logLines(output).flatMap(JavaSmokeTest::findTraceId).collect(toSet());
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
  public TelemetryRetriever getTelemetryRetriever() {
    return telemetryRetriever;
  }
}
