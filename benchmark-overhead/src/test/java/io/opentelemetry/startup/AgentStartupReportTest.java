/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.startup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentStartupReportTest {
  @TempDir Path directory;

  @Test
  void ordinaryLaunchHasNoBootstrapPreloadOrAot() {
    assertThat(AgentStartupBenchmark.arguments("baseline", false))
        .contains("-javaagent:/benchmark/baseline.jar")
        .noneMatch(
            value ->
                value.contains("bootclasspath")
                    || value.contains("AOT")
                    || value.contains("FlightRecording"));
    assertThat(AgentStartupBenchmark.arguments("no-agent", false))
        .noneMatch(value -> value.contains("agent") || value.contains("FlightRecording"));
    assertThat(AgentStartupBenchmark.arguments("candidate", true))
        .contains(
            "-javaagent:/benchmark/candidate.jar",
            "-XX:StartFlightRecording=name=startup,settings=profile");
  }

  @Test
  void reportsPairedSavingsAndIdenticalJarControl() throws Exception {
    AgentStartupReport report = new AgentStartupReport(directory);
    List<AgentStartupReport.Sample> samples = new ArrayList<>();
    for (String phase : List.of("control", "comparison")) {
      for (int round = 0; round < 4; round++) {
        for (String variant : List.of("baseline", "candidate", "no-agent")) {
          if (phase.equals("control") && variant.equals("no-agent")) {
            continue;
          }
          samples.add(
              new AgentStartupReport.Sample(
                  phase,
                  variant,
                  round,
                  0,
                  round == 0,
                  "ok",
                  variant.equals("no-agent")
                      ? 1
                      : variant.equals("candidate") && phase.equals("comparison") ? 2 : 3,
                  1,
                  4,
                  0));
        }
      }
    }
    for (var sample : samples) {
      report.append(sample);
    }
    report.summarize(samples);
    assertThat(Files.readString(directory.resolve("summary.md")))
        .contains("| control | 0.0 | [0.0, 0.0] |")
        .contains("| comparison | 1000.0 | [1000.0, 1000.0] |")
        .contains("baseline 2000.0 ms; candidate 1000.0 ms");
    assertThat(Files.readAllLines(directory.resolve("samples.csv"))).hasSize(21);
  }

  @Test
  void rejectsIncompletePairsAndKeepsSignedUncertainty() {
    assertThatThrownBy(() -> AgentStartupReport.differences(List.of(), "comparison"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(AgentStartupReport.interval(List.of(-1.0, -1.0, 1.0, 1.0)))
        .containsExactly(-1.0, 1.0);
    assertThat(AgentStartupReport.interval(List.of(-2.0, -2.0, -2.0))).containsExactly(-2.0, -2.0);
  }
}
