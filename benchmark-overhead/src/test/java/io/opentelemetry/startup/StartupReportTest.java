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
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StartupReportTest {
  @TempDir Path temporaryDirectory;

  @Test
  void writesIncrementalSamplesAndSummaryAndChart() throws Exception {
    StartupReport report = new StartupReport(temporaryDirectory);
    List<StartupSample> samples = samples();

    report.writeSamples(samples.subList(0, 4));
    report.writeSamples(samples.subList(4, samples.size()));
    Properties metadata = new Properties();
    metadata.setProperty("reproduction.command", "run --value=a&b");
    report.writeSummary(samples, metadata);

    String csv = Files.readString(temporaryDirectory.resolve("samples.csv"));
    assertThat(csv.lines()).hasSize(samples.size() + 1);
    assertThat(csv).contains("normal-no-agent,0,1,true,ok,1.0,2.0,2.5");
    assertThat(Files.readString(temporaryDirectory.resolve("summary.md")))
        .contains("| AOT, no agent | 2 | 1 |")
        .contains("| With agent | 2.250 | 2.750 | -0.500 | -22.222% |")
        .contains("run --value=a&b");
    String svg = Files.readString(temporaryDirectory.resolve("startup.svg"));
    assertThat(svg)
        .contains("data-variant=\"normal-no-agent\" data-median=\"2.250\"")
        .contains("data-variant=\"aot-no-agent\" data-median=\"2.750\"")
        .contains("class=\"iqr\"");
  }

  @Test
  void reportsSlowerAotWithoutDiscardingTheResult() throws Exception {
    StartupReport report = new StartupReport(temporaryDirectory);
    List<StartupSample> samples = samples();
    samples =
        samples.stream()
            .map(
                sample ->
                    sample.variant() == Variant.AOT_NO_AGENT && !sample.discarded()
                        ? new StartupSample(
                            sample.variant(),
                            sample.round(),
                            sample.order(),
                            false,
                            "ok",
                            sample.springSeconds(),
                            3.0,
                            sample.httpSeconds())
                        : sample)
            .toList();

    report.writeSummary(samples, new Properties());

    assertThat(Files.readString(temporaryDirectory.resolve("summary.md")))
        .contains("| No agent | 2.250 | 3.000 | -0.750 | -33.333% |");
  }

  @Test
  void rejectsFailedOrUnevenResults() throws Exception {
    StartupReport report = new StartupReport(temporaryDirectory);
    List<StartupSample> samples = samples();
    List<StartupSample> failed = new ArrayList<>(samples);
    failed.set(
        1,
        new StartupSample(
            Variant.NORMAL_NO_AGENT, 1, 1, false, "failed", Double.NaN, Double.NaN, Double.NaN));

    assertThatThrownBy(() -> report.writeSummary(failed, new Properties()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> report.writeSummary(samples.subList(0, samples.size() - 1), new Properties()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void usesLinearQuantilesAndEscapesCsvValues() {
    assertThat(StartupReport.quantile(List.of(1.0, 2.0, 4.0, 8.0), 0.25)).isEqualTo(1.75);
    assertThat(StartupReport.csv("a,\"b")).isEqualTo("\"a,\"\"b\"");
  }

  private static List<StartupSample> samples() {
    List<StartupSample> samples = new ArrayList<>();
    for (Variant variant : Variant.values()) {
      double jvm = variant.aot() ? (variant.agent() ? 2.5 : 2.5) : 2.0;
      samples.add(new StartupSample(variant, 0, 1, true, "ok", 1.0, jvm, 2.5));
      samples.add(new StartupSample(variant, 1, 1, false, "ok", 1.0, jvm, 2.5));
      samples.add(new StartupSample(variant, 2, 1, false, "ok", 1.5, jvm + 0.5, 3.0));
    }
    return samples;
  }
}
