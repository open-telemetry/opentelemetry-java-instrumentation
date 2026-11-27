/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.results;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.agents.Agent;
import io.opentelemetry.config.Configs;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MainResultsPersisterTest {
  @TempDir Path directory;

  @Test
  void writesNewSeriesWithoutChangingHistoricalCsv() throws Exception {
    var config = Configs.RELEASE.config;
    Path historical = directory.resolve(config.getName()).resolve("results.csv");
    Files.createDirectories(historical.getParent());
    Files.writeString(historical, "historical\n");
    var result =
        AppPerfResults.builder()
            .agent(Agent.NONE)
            .config(config)
            .heapUsed(new AppPerfResults.MinMax(1, 2))
            .build();
    var persister = new MainResultsPersister(config, directory);
    persister.write(List.of(result));
    persister.write(List.of(result));

    assertThat(Files.readString(historical)).isEqualTo("historical\n");
    Path csv = directory.resolve("petclinic-v2").resolve(config.getName()).resolve("results.csv");
    var lines = Files.readAllLines(csv);
    assertThat(lines).hasSize(3);
    assertThat(lines.get(0)).startsWith("timestamp,").contains(":startupDurationMs");
    assertThat(lines.get(1).split(",")).hasSameSizeAs(lines.get(0).split(","));
    assertThat(Files.exists(csv.getParent().resolve("summary.txt"))).isTrue();
  }
}
