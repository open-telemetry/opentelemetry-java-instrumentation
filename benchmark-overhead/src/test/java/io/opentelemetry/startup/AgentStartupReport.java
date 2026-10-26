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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

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
          "phase,variant,round,order,discarded,status,jvm_seconds,spring_seconds,http_seconds,host_load_average\n",
          UTF_8);
    }
    Files.writeString(
        csv,
        String.format(
            Locale.ROOT,
            "%s,%s,%d,%d,%s,%s,%s,%s,%s,%s%n",
            sample.phase(),
            sample.variant(),
            sample.round(),
            sample.order(),
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
    if (samples.stream()
        .anyMatch(
            sample ->
                !sample.status().equals("ok")
                    || !Double.isFinite(sample.jvm())
                    || sample.jvm() <= 0)) {
      throw new IllegalArgumentException("Cannot summarize failed or invalid samples");
    }
    StringBuilder result = new StringBuilder("# Ordinary Java agent startup\n\n");
    result.append(
        "Primary metric: JVM uptime at Spring startup completion, including premain. All durations below are milliseconds.\n\n");
    result.append(
        "| Phase | Variant | Starts | JVM median | JVM p25 | JVM p75 | JVM p90 | HTTP median |\n");
    result.append("| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |\n");
    for (String phase : List.of("control", "comparison")) {
      for (String variant : List.of("baseline", "candidate", "no-agent")) {
        List<Sample> rows = measured(samples, phase, variant);
        if (rows.isEmpty() && phase.equals("control") && variant.equals("no-agent")) {
          continue;
        }
        List<Double> jvm = rows.stream().map(Sample::jvm).toList();
        List<Double> http = rows.stream().map(Sample::http).toList();
        result.append(
            String.format(
                Locale.ROOT,
                "| %s | %s | %d | %.1f | %.1f | %.1f | %.1f | %.1f |\n",
                phase,
                variant,
                rows.size(),
                1000 * quantile(jvm, .5),
                1000 * quantile(jvm, .25),
                1000 * quantile(jvm, .75),
                1000 * quantile(jvm, .9),
                1000 * quantile(http, .5)));
      }
    }
    result.append("\n| Phase | Median paired saving | 95% block-bootstrap interval |\n");
    result.append("| --- | ---: | ---: |\n");
    for (String phase : List.of("control", "comparison")) {
      List<Double> differences = differences(samples, phase);
      List<Double> interval = interval(differences);
      result.append(
          String.format(
              Locale.ROOT,
              "| %s | %.1f | [%.1f, %.1f] |\n",
              phase,
              1000 * quantile(differences, .5),
              1000 * interval.get(0),
              1000 * interval.get(1)));
    }
    double noAgent =
        quantile(
            measured(samples, "comparison", "no-agent").stream().map(Sample::jvm).toList(), .5);
    double baseline =
        quantile(
            measured(samples, "comparison", "baseline").stream().map(Sample::jvm).toList(), .5);
    double candidate =
        quantile(
            measured(samples, "comparison", "candidate").stream().map(Sample::jvm).toList(), .5);
    result.append(
        String.format(
            Locale.ROOT,
            "\nAgent-added median startup: baseline %.1f ms; candidate %.1f ms. Difference of medians: %.1f ms (%.2f%% of baseline startup; %.2f%% of baseline agent-added overhead).\n",
            1000 * (baseline - noAgent),
            1000 * (candidate - noAgent),
            1000 * (baseline - candidate),
            100 * (baseline - candidate) / baseline,
            100 * (baseline - candidate) / (baseline - noAgent)));
    result.append(
        "\nPositive paired savings favor the candidate. Control uses the identical baseline JAR for both labels. Intervals resample consecutive round blocks, with block length ceil(sqrt(rounds)), using 5,000 deterministic bootstrap replicates. They do not account for every source of host noise; repeat improvements in an independent run.\n\n");
    result.append(
        "All starts are retained, including discarded fresh-process warmups. Failed starts abort the run without a summary. Filesystem/image caches are warm; this is not cold-machine startup. Exporters are disabled, instrumentation and SDK remain active. JFR runs are separate and excluded from timing samples. HTTP duration includes Docker and harness overhead. See metadata.properties for exact artifacts, commands, and resources.\n");
    Files.writeString(directory.resolve("summary.md"), result, UTF_8);
  }

  static List<Double> differences(List<Sample> samples, String phase) {
    List<Sample> before = measured(samples, phase, "baseline");
    List<Sample> after = measured(samples, phase, "candidate");
    if (before.size() < 2 || before.size() != after.size()) {
      throw new IllegalArgumentException("At least two complete paired rounds are required");
    }
    List<Double> differences = new ArrayList<>();
    for (int i = 0; i < before.size(); i++) {
      if (before.get(i).round() != after.get(i).round()) {
        throw new IllegalArgumentException("Round identities do not match");
      }
      differences.add(before.get(i).jvm() - after.get(i).jvm());
    }
    return differences;
  }

  static List<Double> interval(List<Double> differences) {
    if (differences.size() < 2) {
      throw new IllegalArgumentException("At least two pairs are required");
    }
    Random random = new Random(253);
    int block = (int) Math.ceil(Math.sqrt(differences.size()));
    List<Double> estimates = new ArrayList<>();
    for (int iteration = 0; iteration < 5000; iteration++) {
      List<Double> resampled = new ArrayList<>();
      while (resampled.size() < differences.size()) {
        int start = random.nextInt(differences.size());
        for (int offset = 0; offset < block && resampled.size() < differences.size(); offset++) {
          resampled.add(differences.get((start + offset) % differences.size()));
        }
      }
      estimates.add(quantile(resampled, .5));
    }
    return List.of(quantile(estimates, .025), quantile(estimates, .975));
  }

  private static List<Sample> measured(List<Sample> samples, String phase, String variant) {
    return samples.stream()
        .filter(
            sample ->
                !sample.discarded()
                    && sample.phase().equals(phase)
                    && sample.variant().equals(variant))
        .sorted(java.util.Comparator.comparingInt(Sample::round))
        .toList();
  }

  private static double quantile(List<Double> values, double q) {
    return StartupReport.quantile(values, q);
  }

  record Sample(
      String phase,
      String variant,
      int round,
      int order,
      boolean discarded,
      String status,
      double jvm,
      double spring,
      double http,
      double load) {}
}
