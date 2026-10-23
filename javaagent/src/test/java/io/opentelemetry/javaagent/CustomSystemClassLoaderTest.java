/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import jvmbootstraptest.AgentStartupProbe;
import jvmbootstraptest.ChildFirstSystemClassLoader;
import org.junit.jupiter.api.Test;

class CustomSystemClassLoaderTest {

  private static final String CUSTOM_LOADER = ChildFirstSystemClassLoader.class.getName();
  private static final String STARTUP_ERROR = "ERROR io.opentelemetry.javaagent.OpenTelemetryAgent";
  private static final String BOOTSTRAP_ASSERTION =
      "java.lang.IllegalStateException: agent initializer should be loaded in boot loader";

  @Test
  void normalSystemLoaderStartsAgent() throws Exception {
    assertStarted(run());
  }

  @Test
  void parentFirstCustomSystemLoaderStartsAgent() throws Exception {
    IntegrationTestUtils.ProcessResult result =
        run("-Djava.system.class.loader=" + CUSTOM_LOADER, "-Drepro.parent-first=true");

    assertStarted(result);
    assertThat(result.getOutput())
        .contains("system-loader=" + CUSTOM_LOADER, "premain-loader=" + CUSTOM_LOADER);
  }

  @Test
  void childFirstCustomSystemLoaderReproducesBootstrapAssertion() throws Exception {
    IntegrationTestUtils.ProcessResult result = run("-Djava.system.class.loader=" + CUSTOM_LOADER);

    // premain catches the exception: the application still runs with an uninitialized agent.
    assertThat(result.getExitCode()).isZero();
    assertThat(result.getOutput())
        .contains(
            STARTUP_ERROR,
            BOOTSTRAP_ASSERTION,
            "at io.opentelemetry.javaagent.bootstrap.AgentInitializer.initialize(",
            "at io.opentelemetry.javaagent.OpenTelemetryAgent.startAgent(",
            "at io.opentelemetry.javaagent.OpenTelemetryAgent.premain(",
            "premain-loader=" + CUSTOM_LOADER,
            "initializer-loader=" + CUSTOM_LOADER,
            "agent-started=false",
            "same-bootstrap-initializer=false",
            "application-main-completed");
  }

  private static IntegrationTestUtils.ProcessResult run(String... args) throws Exception {
    List<String> jvmArgs = new ArrayList<>(asList(args));
    jvmArgs.add("-Dotel.traces.exporter=none");
    jvmArgs.add("-Dotel.metrics.exporter=none");
    jvmArgs.add("-Dotel.logs.exporter=none");
    return IntegrationTestUtils.runOnSeparateJvmAndCaptureOutput(
        AgentStartupProbe.class.getName(),
        jvmArgs.toArray(new String[0]),
        new String[0],
        emptyMap(),
        System.getProperty("java.class.path"),
        true);
  }

  private static void assertStarted(IntegrationTestUtils.ProcessResult result) {
    assertThat(result.getExitCode()).isZero();
    assertThat(result.getOutput())
        .contains(
            "initializer-loader=bootstrap",
            "agent-started=true",
            "same-bootstrap-initializer=true",
            "application-main-completed")
        .doesNotContain(STARTUP_ERROR, BOOTSTRAP_ASSERTION);
  }
}
