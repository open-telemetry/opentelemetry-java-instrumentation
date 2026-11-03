/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.startup;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

final class AgentStartupReport {
  private final Path directory;

  AgentStartupReport(Path directory) {
    this.directory = directory;
  }

  void append(Sample sample) throws IOException {
    Path csv = directory.resolve("samples.csv");
    if (!Files.exists(csv)) {
      Files.writeString(
          csv, "configuration,round,discarded,status,startup_seconds,host_load_average\n", UTF_8);
    }
    Files.writeString(
        csv,
        String.format(
            Locale.ROOT,
            "%s,%d,%s,%s,%s,%s%n",
            sample.configuration(),
            sample.round(),
            sample.discarded(),
            sample.status(),
            sample.startupSeconds(),
            sample.load()),
        UTF_8,
        StandardOpenOption.APPEND);
  }

  void summarize(List<Sample> samples) throws IOException {
    List<Sample> measured = samples.stream().filter(sample -> !sample.discarded()).toList();
    if (measured.isEmpty()) {
      throw new IllegalArgumentException("At least one measured sample is required");
    }
    for (Sample sample : samples) {
      if (!sample.status().equals("ok") || !valid(sample.startupSeconds())) {
        throw new IllegalArgumentException("Cannot summarize failed or invalid samples");
      }
    }
    Map<String, List<Double>> configurations =
        measured.stream()
            .collect(
                Collectors.groupingBy(
                    Sample::configuration,
                    LinkedHashMap::new,
                    Collectors.mapping(Sample::startupSeconds, Collectors.toList())));
    List<Double> noAgent = configurations.get("no-agent");
    if (noAgent == null) {
      throw new IllegalArgumentException("A measured no-agent control is required");
    }
    double noAgentMedian = quantile(noAgent, .5);
    StringBuilder result = new StringBuilder("# Ordinary Java agent startup\n\n");
    result.append(
        "The metric is JVM uptime sampled by the `/greeting` handler while producing its successful `Hi!` response. It includes JVM startup and agent `premain` when enabled; all durations below are milliseconds. Agent-added overhead is each configuration's median minus the no-agent median, not a median of paired differences.\n\n");
    result.append(
        "| Configuration | Measured starts | p25 | Median | p75 | p90 | Agent-added overhead |\n");
    result.append("| --- | ---: | ---: | ---: | ---: | ---: | ---: |\n");
    configurations.forEach(
        (name, values) -> appendDistribution(result, name, values, noAgentMedian));
    if (configurations.containsKey("baseline-agent") && configurations.containsKey("agent")) {
      double baselineOverhead = quantile(configurations.get("baseline-agent"), .5) - noAgentMedian;
      double overheadReduction =
          quantile(configurations.get("baseline-agent"), .5)
              - quantile(configurations.get("agent"), .5);
      result.append(
          String.format(
              Locale.ROOT, "\nAgent-added overhead reduction: %.1f ms", 1000 * overheadReduction));
      if (baselineOverhead > 0) {
        result.append(
            String.format(
                Locale.ROOT,
                ", %.2f%% of baseline agent-added overhead.\n",
                100 * overheadReduction / baselineOverhead));
      } else {
        result.append(
            ". Relative reduction is undefined because measured baseline overhead is not positive.\n");
      }
    }
    result.append(
        "\nAll starts, including discarded fresh-process warmups, are retained in samples.csv. Configurations run serially in rotating order each round. Failed starts abort the run without a summary. The application reports its JVM-relative uptime, excluding Docker setup and host-side request timing. Filesystem/image caches are warm; this is not cold-machine startup. Agent configurations keep instrumentation and SDK active with exporters disabled. Separate JFR runs are excluded from timing samples. See metadata.properties for exact artifacts, source revisions, commands, image/JDK identity, and resources.\n");
    Files.writeString(directory.resolve("summary.md"), result, UTF_8);
  }

  private static void appendDistribution(
      StringBuilder result, String configuration, List<Double> values, double noAgentMedian) {
    result.append(
        String.format(
            Locale.ROOT,
            "| %s | %d | %.1f | %.1f | %.1f | %.1f | %.1f |\n",
            configuration,
            values.size(),
            1000 * quantile(values, .25),
            1000 * quantile(values, .5),
            1000 * quantile(values, .75),
            1000 * quantile(values, .9),
            1000 * (quantile(values, .5) - noAgentMedian)));
  }

  private static boolean valid(double seconds) {
    return Double.isFinite(seconds) && seconds > 0;
  }

  private static double quantile(List<Double> values, double q) {
    return StartupReport.quantile(values, q);
  }

  record Sample(
      String configuration,
      int round,
      boolean discarded,
      String status,
      double startupSeconds,
      double load) {}
}
