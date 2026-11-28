/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.results;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.agents.Agent;
import io.opentelemetry.config.Configs;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LoadBenchmarkReportTest {
  @TempDir Path directory;

  @Test
  void reportsLatencyCpuAndPausesInDisplayUnits() throws Exception {
    var config = Configs.manualPetclinic(directory);
    var result =
        AppPerfResults.builder()
            .agent(Agent.NONE)
            .config(config)
            .requestP95(4.56789)
            .averageJvmUserCpu(0.125f)
            .totalGcPauseNanos(2_345_678)
            .build();
    Path summary = directory.resolve("reports/summary.md");

    LoadBenchmarkReport.write(summary, config, List.of(result));

    assertThat(Files.readString(summary))
        .contains("5,000 iterations per variant, 5 virtual users, 60 seconds of warmup")
        .contains("| none | 4.568 | 12.500 | 2.346 |");
  }

  @Test
  void rejectsIncompleteK6Runs() {
    ResultsCollector.verifyIterations(
        "{\"metrics\":{\"iterations\":{\"count\":5000}}}", 5000, "none");
    assertThatThrownBy(() ->
            ResultsCollector.verifyIterations(
                "{\"metrics\":{\"iterations\":{\"count\":4999}}}", 5000, "revision"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("revision completed 4999.0 of 5000 k6 iterations");
  }
}
