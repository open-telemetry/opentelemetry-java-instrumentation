/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.results;

import io.opentelemetry.config.TestConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class LoadBenchmarkReport {

  public static void write(Path path, TestConfig config, List<AppPerfResults> results)
      throws IOException {
    StringBuilder summary = new StringBuilder("### Petclinic load\n\n");
    summary.append(
        String.format(
            Locale.ROOT,
            "%,d iterations per variant, %d virtual users, %d seconds of warmup, no request-rate cap. "
                + "This is a fixed-iteration workload, not a fixed-rate comparison.%n%n",
            config.getTotalIterations(),
            config.getConcurrentConnections(),
            config.getWarmupSeconds()));
    summary.append("| Agent | Request p95 (ms) | Avg. JVM user CPU (%) | Total GC pause (ms) |\n");
    summary.append("| --- | ---: | ---: | ---: |\n");
    for (AppPerfResults result : results) {
      summary.append(
          String.format(
              Locale.ROOT,
              "| %s | %.3f | %.3f | %.3f |%n",
              result.getAgentName(),
              result.requestP95,
              result.averageJvmUserCpu * 100.0,
              result.totalGcPauseNanos / 1_000_000.0));
    }
    Files.createDirectories(path.getParent());
    Files.writeString(path, summary);
  }

  private LoadBenchmarkReport() {}
}
