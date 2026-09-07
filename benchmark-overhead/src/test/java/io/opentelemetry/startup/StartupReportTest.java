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
    metadata.setProperty(
        "jvm.version", "openjdk version \"25.0.1\" 2025-10-21 LTS\nOpenJDK Runtime Environment");
    metadata.setProperty("spring.version", "/app/libs/spring-boot-2.6.15.jar");
    metadata.setProperty("agent.version", "2.32.0-SNAPSHOT");
    metadata.setProperty("cpus", "2");
    metadata.setProperty("memory.bytes", "1073741824");
    metadata.setProperty("heap", "-Xmx512m");
    report.writeSummary(samples, metadata);

    String csv = Files.readString(temporaryDirectory.resolve("samples.csv"));
    assertThat(csv.lines()).hasSize(samples.size() + 1);
    assertThat(csv).contains("normal-no-agent,0,1,true,ok,1.0,2.0,2.5");
    assertThat(Files.readString(temporaryDirectory.resolve("summary.md")))
        .contains("| AOT, no agent | 2 | 1 |")
        .contains("| With agent | 2.250 | 2.750 | -0.500 | -22.222% |")
        .contains(
            "Environment: JDK 25.0.1; Spring Boot 2.6.15; agent 2.32.0-SNAPSHOT; resources 2 CPU / 1 GiB / -Xmx512m; traces/metrics/logs exporters disabled; instrumentation active")
        .contains("AOT was slower than normal in both comparison pairs")
        .contains("run --value=a&b");
    String svg = Files.readString(temporaryDirectory.resolve("startup.svg"));
    assertThat(svg)
        .contains("JDK 25 JVM uptime at Spring startup completion")
        .contains("JVM uptime at Spring startup completion (seconds)")
        .contains(
            "JDK 25.0.1; Spring Boot 2.6.15; agent 2.32.0-SNAPSHOT; resources 2 CPU / 1 GiB / -Xmx512m; traces/metrics/logs exporters disabled; instrumentation active")
        .contains("2.250 s (n=2)")
        .contains("AOT: -0.500 s saved (-22.222%)")
        .contains("data-variant=\"normal-no-agent\" data-median=\"2.250\"")
        .contains("data-variant=\"aot-no-agent\" data-median=\"2.750\"")
        .contains("class=\"iqr\"");
  }

  @Test
  void describesSmallerRelativeGainAndLargerAbsoluteSavingWithAgent() throws Exception {
    StartupReport report = new StartupReport(temporaryDirectory);

    report.writeSummary(fasterSamples(), new Properties());

    assertThat(Files.readString(temporaryDirectory.resolve("summary.md")))
        .contains(
            "AOT's relative reduction was smaller with the agent (30.000% versus 50.000% without it), while the absolute time saved was larger (3.000 seconds versus 2.000 seconds).");
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

  private static List<StartupSample> fasterSamples() {
    List<StartupSample> samples = new ArrayList<>();
    for (Variant variant : Variant.values()) {
      double normal = variant.agent() ? 10 : 4;
      double aot = variant.aot() ? (variant.agent() ? 7 : 2) : normal;
      samples.add(new StartupSample(variant, 0, 1, true, "ok", 1, normal, 2));
      samples.add(new StartupSample(variant, 1, 1, false, "ok", 1, aot, 2));
      samples.add(new StartupSample(variant, 2, 1, false, "ok", 1, aot, 2));
    }
    return samples;
  }
}
