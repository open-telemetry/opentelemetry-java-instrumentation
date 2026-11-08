/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent;

import static java.util.Collections.emptyMap;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import jvmbootstraptest.Log4jStartupProbe;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@EnabledForJreRange(min = JRE.JAVA_11)
class Log4jStartupTest {
  @ParameterizedTest(name = "withJpl={0}")
  @ValueSource(booleans = {false, true})
  void correlatesLogsWhenPrometheusStarts(boolean withJpl) throws Exception {
    String probeClasses =
        new File(
                Log4jStartupProbe.class.getProtectionDomain().getCodeSource().getLocation().toURI())
            .getPath();
    String classpath =
        probeClasses + File.pathSeparator + System.getProperty("log4jStartup.runtime");
    if (withJpl) {
      classpath += File.pathSeparator + System.getProperty("log4jStartup.jpl");
    }

    IntegrationTestUtils.ProcessResult result =
        IntegrationTestUtils.runOnSeparateJvmAndCaptureOutput(
            Log4jStartupProbe.class.getName(),
            new String[] {
              "-Dotel.metrics.exporter=prometheus",
              "-Dotel.exporter.prometheus.host=127.0.0.1",
              "-Dotel.exporter.prometheus.port=0",
              "-Dotel.traces.exporter=console",
              "-Dotel.logs.exporter=none",
              "-Dlog4j2.configurationFile="
                  + requireNonNull(getClass().getResource("log4j-startup.xml")).toExternalForm()
            },
            new String[] {Boolean.toString(withJpl)},
            emptyMap(),
            classpath,
            false);

    String output = result.getOutput();
    assertThat(result.getExitCode()).as("Child JVM output:%n%s", output).isZero();
    String[] lines = output.split("\\R");
    String correlation =
        Arrays.stream(lines)
            .filter(line -> line.startsWith("SPAN|"))
            .findFirst()
            .orElseThrow(() -> new AssertionError(output))
            .substring("SPAN|".length());
    List<String> expectedLogs = new ArrayList<>();
    expectedLogs.add("LOG|inside span|" + correlation + "|application-value");
    if (withJpl) {
      String[] spanContext = correlation.split("\\|");
      expectedLogs.add(
          "LOG|inside span with application span ID|"
              + spanContext[0]
              + "|application-span-id|"
              + spanContext[2]
              + "|application-value");
    }
    expectedLogs.add("LOG|outside span||||");
    assertThat(Arrays.stream(lines).filter(line -> line.startsWith("LOG|")))
        .as("Log4j event correlation with JPL enabled: %s", withJpl)
        .containsExactlyElementsOf(expectedLogs);
  }
}
