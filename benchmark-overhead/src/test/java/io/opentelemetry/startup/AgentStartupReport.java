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
import java.util.List;
import java.util.Locale;

final class AgentStartupReport {
  private final Path directory;

  AgentStartupReport(Path directory) {
    this.directory = directory;
  }

  void append(Sample sample) throws IOException {
    Path csv = directory.resolve("samples.csv");
    if (!Files.exists(csv)) {
      Files.writeString(
          csv,
          "round,discarded,status,jvm_seconds,spring_seconds,http_seconds,host_load_average\n",
          UTF_8);
    }
    Files.writeString(
        csv,
        String.format(
            Locale.ROOT,
            "%d,%s,%s,%s,%s,%s,%s%n",
            sample.round(),
            sample.discarded(),
            sample.status(),
            sample.jvm(),
            sample.spring(),
            sample.http(),
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
      if (!sample.status().equals("ok")
          || !valid(sample.jvm())
          || !valid(sample.spring())
          || !valid(sample.http())
          || sample.jvm() < sample.spring()) {
        throw new IllegalArgumentException("Cannot summarize failed or invalid samples");
      }
    }
    StringBuilder result = new StringBuilder("# Ordinary Java agent startup\n\n");
    result.append(
        "Primary metric: JVM uptime at Spring startup completion, including premain. All durations below are milliseconds.\n\n");
    result.append("| Metric | Measured starts | p25 | Median | p75 | p90 |\n");
    result.append("| --- | ---: | ---: | ---: | ---: | ---: |\n");
    appendDistribution(result, "JVM uptime", measured.stream().map(Sample::jvm).toList());
    appendDistribution(result, "Spring startup", measured.stream().map(Sample::spring).toList());
    appendDistribution(result, "HTTP readiness", measured.stream().map(Sample::http).toList());
    result.append(
        "\nAll starts, including discarded fresh-process warmups, are retained in samples.csv. Failed starts abort the run without a summary. Filesystem/image caches are warm; this is not cold-machine startup. Exporters are disabled, instrumentation and SDK remain active. The separate JFR run is excluded from timing samples. HTTP duration includes Docker and harness overhead. See metadata.properties for exact artifact, source revision, commands, and resources.\n");
    Files.writeString(directory.resolve("summary.md"), result, UTF_8);
  }

  private static void appendDistribution(StringBuilder result, String metric, List<Double> values) {
    result.append(
        String.format(
            Locale.ROOT,
            "| %s | %d | %.1f | %.1f | %.1f | %.1f |\n",
            metric,
            values.size(),
            1000 * quantile(values, .25),
            1000 * quantile(values, .5),
            1000 * quantile(values, .75),
            1000 * quantile(values, .9)));
  }

  private static boolean valid(double seconds) {
    return Double.isFinite(seconds) && seconds > 0;
  }

  private static double quantile(List<Double> values, double q) {
    return StartupReport.quantile(values, q);
  }

  record Sample(
      int round,
      boolean discarded,
      String status,
      double jvm,
      double spring,
      double http,
      double load) {}
}
