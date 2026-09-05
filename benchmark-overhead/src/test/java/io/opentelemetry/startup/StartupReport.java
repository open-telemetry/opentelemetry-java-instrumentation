/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.startup;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

final class StartupReport {
  private static final String CSV_HEADER =
      "variant,round,order,discarded,status,spring_seconds,jvm_seconds,http_seconds\n";
  private static final int CHART_WIDTH = 900;
  private static final int CHART_HEIGHT = 520;
  private static final int CHART_LEFT = 90;
  private static final int CHART_RIGHT = 35;
  private static final int CHART_TOP = 70;
  private static final int CHART_BOTTOM = 95;

  private final Path directory;

  StartupReport(Path directory) throws IOException {
    this.directory = directory;
    Files.createDirectories(directory);
  }

  void writeSamples(List<StartupSample> samples) throws IOException {
    if (samples.isEmpty()) {
      return;
    }
    Path file = directory.resolve("samples.csv");
    boolean header = !Files.exists(file) || Files.size(file) == 0;
    StringBuilder output = new StringBuilder();
    if (header) {
      output.append(CSV_HEADER);
    }
    for (StartupSample sample : samples) {
      output
          .append(csv(sample.variant().id()))
          .append(',')
          .append(sample.round())
          .append(',')
          .append(sample.order())
          .append(',')
          .append(sample.discarded())
          .append(',')
          .append(csv(sample.status()))
          .append(',')
          .append(Double.toString(sample.springSeconds()))
          .append(',')
          .append(Double.toString(sample.jvmSeconds()))
          .append(',')
          .append(Double.toString(sample.httpSeconds()))
          .append('\n');
    }
    Files.writeString(
        file,
        output,
        UTF_8,
        java.nio.file.StandardOpenOption.CREATE,
        java.nio.file.StandardOpenOption.APPEND);
  }

  void writeSummary(List<StartupSample> samples, Properties metadata) throws IOException {
    List<StartupSample> measured = validate(samples);
    Map<Variant, Statistics> statistics = statistics(measured);
    Files.writeString(
        directory.resolve("summary.md"), renderSummary(statistics, samples, metadata), UTF_8);
    Files.writeString(directory.resolve("startup.svg"), renderChart(statistics), UTF_8);
  }

  static double quantile(List<Double> values, double quantile) {
    if (values.isEmpty() || quantile < 0 || quantile > 1) {
      throw new IllegalArgumentException("Quantile requires non-empty values and q in [0, 1]");
    }
    List<Double> sorted = values.stream().sorted().toList();
    double position = (sorted.size() - 1) * quantile;
    int lower = (int) position;
    int upper = Math.min(lower + 1, sorted.size() - 1);
    return sorted.get(lower) + (sorted.get(upper) - sorted.get(lower)) * (position - lower);
  }

  private static List<StartupSample> validate(List<StartupSample> samples) {
    if (samples.isEmpty()) {
      throw new IllegalArgumentException("Cannot summarize an empty benchmark");
    }
    EnumSet<Variant> variants = EnumSet.noneOf(Variant.class);
    EnumMap<Variant, Integer> counts = new EnumMap<>(Variant.class);
    List<StartupSample> measured = new ArrayList<>();
    for (StartupSample sample : samples) {
      if (!sample.status().equals("ok")) {
        throw new IllegalArgumentException("Cannot summarize a failed startup sample");
      }
      if (!validDuration(sample.springSeconds())
          || !validDuration(sample.jvmSeconds())
          || !validDuration(sample.httpSeconds())
          || sample.jvmSeconds() < sample.springSeconds()) {
        throw new IllegalArgumentException("Cannot summarize invalid startup timings");
      }
      variants.add(sample.variant());
      if (!sample.discarded()) {
        measured.add(sample);
        counts.merge(sample.variant(), 1, Integer::sum);
      }
    }
    if (!variants.equals(EnumSet.allOf(Variant.class))) {
      throw new IllegalArgumentException("Summary requires every benchmark variant");
    }
    if (counts.size() != Variant.values().length
        || counts.values().stream().distinct().count() != 1) {
      throw new IllegalArgumentException("Summary requires equal measured counts per variant");
    }
    return measured;
  }

  private static boolean validDuration(double duration) {
    return Double.isFinite(duration) && duration > 0;
  }

  private static Map<Variant, Statistics> statistics(List<StartupSample> samples) {
    EnumMap<Variant, Statistics> result = new EnumMap<>(Variant.class);
    for (Variant variant : Variant.values()) {
      List<StartupSample> rows =
          samples.stream().filter(sample -> sample.variant() == variant).toList();
      result.put(
          variant,
          new Statistics(
              values(rows, Metric.JVM), values(rows, Metric.SPRING), values(rows, Metric.HTTP)));
    }
    return result;
  }

  private static List<Double> values(List<StartupSample> samples, Metric metric) {
    return samples.stream()
        .map(
            sample ->
                switch (metric) {
                  case JVM -> sample.jvmSeconds();
                  case SPRING -> sample.springSeconds();
                  case HTTP -> sample.httpSeconds();
                })
        .toList();
  }

  private static String renderSummary(
      Map<Variant, Statistics> statistics, List<StartupSample> samples, Properties metadata) {
    StringBuilder result = new StringBuilder();
    result.append("# JDK 25 AOT startup benchmark\n\n");
    result.append(
        "Primary metric: JVM uptime at Spring startup completion, including agent premain and "
            + "work before Spring's timer starts. Values are seconds.\n\n");
    result.append(
        "| Variant | Measured | Discarded | JVM median | JVM p25 | JVM p75 | Spring median | "
            + "Spring p25 | Spring p75 | HTTP median | HTTP p25 | HTTP p75 |\n");
    result.append(
        "| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |\n");
    int measuredCount = statistics.get(Variant.NORMAL_NO_AGENT).jvm().size();
    for (Variant variant : Variant.values()) {
      Statistics stats = statistics.get(variant);
      long discarded =
          samples.stream()
              .filter(sample -> sample.variant() == variant && sample.discarded())
              .count();
      result
          .append("| ")
          .append(variant.label())
          .append(" | ")
          .append(measuredCount)
          .append(" | ")
          .append(discarded)
          .append(" | ")
          .append(format(stats.jvm().median()))
          .append(" | ")
          .append(format(stats.jvm().p25()))
          .append(" | ")
          .append(format(stats.jvm().p75()))
          .append(" | ")
          .append(format(stats.spring().median()))
          .append(" | ")
          .append(format(stats.spring().p25()))
          .append(" | ")
          .append(format(stats.spring().p75()))
          .append(" | ")
          .append(format(stats.http().median()))
          .append(" | ")
          .append(format(stats.http().p25()))
          .append(" | ")
          .append(format(stats.http().p75()))
          .append(" |\n");
    }
    result.append("\n## Paired AOT reductions\n\n");
    result.append("| Pair | Normal median | AOT median | Seconds saved | Reduction |\n");
    result.append("| --- | ---: | ---: | ---: | ---: |\n");
    appendReduction(
        result,
        "No agent",
        statistics.get(Variant.NORMAL_NO_AGENT),
        statistics.get(Variant.AOT_NO_AGENT));
    appendReduction(
        result,
        "With agent",
        statistics.get(Variant.NORMAL_AGENT),
        statistics.get(Variant.AOT_AGENT));
    result.append("\n## Caveats\n\n");
    result.append(
        "- Repeated fresh-process starts with warmed filesystem and image caches; this is not "
            + "cold-machine startup or a JVM warmup benchmark.\n");
    result.append(
        "- The workload is the pinned small Spring smoke application. Container-to-HTTP time "
            + "includes Docker and harness overhead and is secondary.\n");
    result.append(
        "- The normal agent case uses the AOT-compatible bootstrap preload without an AOT cache; "
            + "the comparison is not an isolated transformer-installation measurement.\n");
    String command = metadata.getProperty("reproduction.command");
    if (command != null) {
      result.append("\nReproduction: `").append(command).append("`\n");
    }
    return result.toString();
  }

  private static void appendReduction(
      StringBuilder result, String label, Statistics normal, Statistics aot) {
    double saved = normal.jvm().median() - aot.jvm().median();
    result
        .append("| ")
        .append(label)
        .append(" | ")
        .append(format(normal.jvm().median()))
        .append(" | ")
        .append(format(aot.jvm().median()))
        .append(" | ")
        .append(format(saved))
        .append(" | ")
        .append(format(saved / normal.jvm().median() * 100))
        .append("% |\n");
  }

  private static String renderChart(Map<Variant, Statistics> statistics) {
    double max = 0;
    for (Statistics stats : statistics.values()) {
      max = Math.max(max, stats.jvm().p75());
    }
    max = Math.max(max * 1.2, 1);
    int plotWidth = CHART_WIDTH - CHART_LEFT - CHART_RIGHT;
    int plotHeight = CHART_HEIGHT - CHART_TOP - CHART_BOTTOM;
    StringBuilder svg =
        new StringBuilder()
            .append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            .append(
                "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\""
                    + CHART_WIDTH
                    + "\" height=\""
                    + CHART_HEIGHT
                    + "\" viewBox=\"0 0 "
                    + CHART_WIDTH
                    + " "
                    + CHART_HEIGHT
                    + "\">\n")
            .append("<title>JDK 25 Spring startup comparison</title>\n")
            .append(
                "<style>text{font-family:Arial,sans-serif;fill:#202124} .bar{fill:#4c78a8} "
                    + ".iqr{stroke:#202124;stroke-width:3} .grid{stroke:#d9d9d9}</style>\n")
            .append("<text x=\"")
            .append(CHART_WIDTH / 2)
            .append(
                "\" y=\"30\" text-anchor=\"middle\" font-size=\"20\">JDK 25 Spring startup comparison</text>\n");
    svg.append("<text x=\"")
        .append(CHART_WIDTH - CHART_RIGHT)
        .append(
            "\" y=\"30\" text-anchor=\"end\" font-size=\"12\">bars: median; lines: p25-p75 (IQR)</text>\n");
    for (int tick = 0; tick <= 4; tick++) {
      double value = max * tick / 4;
      int y = CHART_TOP + plotHeight - (int) Math.round(value / max * plotHeight);
      svg.append("<line class=\"grid\" x1=\"")
          .append(CHART_LEFT)
          .append("\" x2=\"")
          .append(CHART_WIDTH - CHART_RIGHT)
          .append("\" y1=\"")
          .append(y)
          .append("\" y2=\"")
          .append(y)
          .append("\"/>\n");
      svg.append("<text x=\"")
          .append(CHART_LEFT - 10)
          .append("\" y=\"")
          .append(y + 5)
          .append("\" text-anchor=\"end\" font-size=\"12\">")
          .append(format(value))
          .append("</text>\n");
    }
    int groupWidth = plotWidth / 2;
    int barWidth = 110;
    int[] centers = {CHART_LEFT + groupWidth / 2, CHART_LEFT + groupWidth + groupWidth / 2};
    Variant[][] groups = {
      {Variant.NORMAL_NO_AGENT, Variant.AOT_NO_AGENT},
      {Variant.NORMAL_AGENT, Variant.AOT_AGENT}
    };
    for (int group = 0; group < groups.length; group++) {
      svg.append("<text x=\"")
          .append(centers[group])
          .append("\" y=\"")
          .append(CHART_HEIGHT - 50)
          .append("\" text-anchor=\"middle\" font-size=\"14\">")
          .append(group == 0 ? "Without agent" : "With agent")
          .append("</text>\n");
      for (int index = 0; index < groups[group].length; index++) {
        Variant variant = groups[group][index];
        Statistics stats = statistics.get(variant);
        int center = centers[group] + (index == 0 ? -barWidth / 2 - 12 : barWidth / 2 + 12);
        int x = center - barWidth / 2;
        int y = CHART_TOP + plotHeight - (int) Math.round(stats.jvm().median() / max * plotHeight);
        int base = CHART_TOP + plotHeight;
        int p25 = CHART_TOP + plotHeight - (int) Math.round(stats.jvm().p25() / max * plotHeight);
        int p75 = CHART_TOP + plotHeight - (int) Math.round(stats.jvm().p75() / max * plotHeight);
        svg.append("<rect class=\"bar\" data-variant=\"")
            .append(variant.id())
            .append("\" data-median=\"")
            .append(format(stats.jvm().median()))
            .append("\" x=\"")
            .append(x)
            .append("\" y=\"")
            .append(y)
            .append("\" width=\"")
            .append(barWidth)
            .append("\" height=\"")
            .append(base - y)
            .append("\"/>\n");
        svg.append("<line class=\"iqr\" data-variant=\"")
            .append(variant.id())
            .append("\" x1=\"")
            .append(center)
            .append("\" x2=\"")
            .append(center)
            .append("\" y1=\"")
            .append(p75)
            .append("\" y2=\"")
            .append(p25)
            .append("\"/>\n");
        svg.append("<text x=\"")
            .append(center)
            .append("\" y=\"")
            .append(base + 20)
            .append("\" text-anchor=\"middle\" font-size=\"12\">")
            .append(variant.aot() ? "AOT" : "Normal")
            .append("</text>\n");
      }
    }
    svg.append("<text x=\"")
        .append(CHART_LEFT - 55)
        .append("\" y=\"")
        .append(CHART_TOP + plotHeight / 2)
        .append("\" text-anchor=\"middle\" font-size=\"13\" transform=\"rotate(-90 ")
        .append(CHART_LEFT - 55)
        .append(" ")
        .append(CHART_TOP + plotHeight / 2)
        .append(")\">JVM uptime (seconds)</text>\n");
    return svg.append("</svg>\n").toString();
  }

  static String csv(String value) {
    if (value.indexOf(',') < 0 && value.indexOf('"') < 0 && value.indexOf('\n') < 0) {
      return value;
    }
    return '"' + value.replace("\"", "\"\"") + '"';
  }

  private static String format(double value) {
    return String.format(Locale.ROOT, "%.3f", value);
  }

  private enum Metric {
    SPRING,
    JVM,
    HTTP
  }

  private record Statistics(Values jvm, Values spring, Values http) {
    Statistics(List<Double> jvm, List<Double> spring, List<Double> http) {
      this(new Values(jvm), new Values(spring), new Values(http));
    }
  }

  private record Values(List<Double> values) {
    int size() {
      return values.size();
    }

    double median() {
      return quantile(values, 0.5);
    }

    double p25() {
      return quantile(values, 0.25);
    }

    double p75() {
      return quantile(values, 0.75);
    }
  }
}
