/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import jvmbootstraptest.AgentLoadedChecker;
import org.junit.jupiter.api.Test;

// the configuration file is read before a logging implementation is selected, so its error goes
// through the early log buffer and is replayed (or discarded) when logging is initialized
class EarlyAgentLoggingTest {

  private static final String MISSING_CONFIG_FILE = "/does/not/exist/otel.properties";
  private static final String CONFIG_FILE_ERROR =
      "Configuration file \"" + MISSING_CONFIG_FILE + "\" not found.";

  @Test
  void logsBufferedErrorOnceAfterLoggingIsInitialized() throws Exception {
    IntegrationTestUtils.ProcessResult result = runAgent();

    assertThat(result.getExitCode()).isZero();
    assertThat(countOccurrences(result.getOutput(), CONFIG_FILE_ERROR)).isEqualTo(1);
  }

  @Test
  void discardsBufferedErrorWhenLoggingIsDisabled() throws Exception {
    IntegrationTestUtils.ProcessResult result = runAgent("-Dotel.javaagent.logging=none");

    assertThat(result.getExitCode()).isZero();
    assertThat(result.getOutput()).doesNotContain(CONFIG_FILE_ERROR);
  }

  private static IntegrationTestUtils.ProcessResult runAgent(String... extraJvmArgs)
      throws Exception {
    List<String> jvmArgs = new ArrayList<>();
    jvmArgs.add("-Dotel.javaagent.configuration-file=" + MISSING_CONFIG_FILE);
    jvmArgs.add("-Dotel.traces.exporter=none");
    jvmArgs.add("-Dotel.metrics.exporter=none");
    jvmArgs.add("-Dotel.logs.exporter=none");
    Collections.addAll(jvmArgs, extraJvmArgs);
    return IntegrationTestUtils.runOnSeparateJvmAndCaptureOutput(
        AgentLoadedChecker.class.getName(),
        jvmArgs.toArray(new String[0]),
        new String[] {IntegrationTestUtils.getAgentJarPath()},
        emptyMap(),
        System.getProperty("java.class.path"),
        true);
  }

  private static int countOccurrences(String text, String fragment) {
    int count = 0;
    int index = text.indexOf(fragment);
    while (index != -1) {
      count++;
      index = text.indexOf(fragment, index + fragment.length());
    }
    return count;
  }
}
