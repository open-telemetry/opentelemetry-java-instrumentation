/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.startup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentStartupReportTest {
  @TempDir Path directory;

  @Test
  void launchesOnlyTheSuppliedAgentAndKeepsProfilingSeparate() {
    assertThat(AgentStartupBenchmark.arguments(false))
        .contains("-Xmx512m", "-javaagent:/tmp/agent.jar")
        .noneMatch(
            value ->
                value.contains("bootclasspath")
                    || value.contains("AOT")
                    || value.contains("FlightRecord")
                    || value.contains("startup-recorder")
                    || value.contains("otel.sdk.disabled")
                    || value.contains("otel.instrumentation"));
    assertThat(AgentStartupBenchmark.arguments(true))
        .contains(
            "-XX:FlightRecorderOptions=stackdepth=256",
            "-javaagent:/tmp/agent.jar",
            "-javaagent:/tmp/startup-recorder.jar=/tmp/startup.jfr");
    assertThat(AgentStartupBenchmark.arguments(true))
        .containsSubsequence(
            "-javaagent:/tmp/startup-recorder.jar=/tmp/startup.jfr", "-javaagent:/tmp/agent.jar");
  }

  @Test
  void acceptsOnlyTheSuccessfulReadinessResponseAndJVMUptime() {
    assertThat(AgentStartupBenchmark.startupSeconds(200, "Hi!", "1250")).isEqualTo(1.25);
    assertThatThrownBy(() -> AgentStartupBenchmark.startupSeconds(503, "Hi!", "1250"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> AgentStartupBenchmark.startupSeconds(200, "Not ready", "1250"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> AgentStartupBenchmark.startupSeconds(200, "Hi!", null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> AgentStartupBenchmark.startupSeconds(200, "Hi!", "invalid"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> AgentStartupBenchmark.startupSeconds(200, "Hi!", "0"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void retainsWarmupsButExcludesThemFromSingleSampleDistributions() throws Exception {
    AgentStartupReport report = new AgentStartupReport(directory);
    var warmup = new AgentStartupReport.Sample(0, true, "ok", 3, 0);
    var measured = new AgentStartupReport.Sample(1, false, "ok", 2, 0);
    List<AgentStartupReport.Sample> samples = List.of(warmup, measured);
    report.append(warmup);
    report.append(measured);
    report.summarize(samples);
    assertThat(Files.readString(directory.resolve("summary.md")))
        .contains("| JVM startup to `/greeting` | 1 | 2000.0 | 2000.0 | 2000.0 | 2000.0 |")
        .doesNotContain("Spring startup", "HTTP readiness")
        .doesNotContain("paired", "baseline", "candidate");
    assertThat(Files.readAllLines(directory.resolve("samples.csv")))
        .containsExactly(
            "round,discarded,status,startup_seconds,host_load_average",
            "0,true,ok,3.0,0.0",
            "1,false,ok,2.0,0.0");
  }

  @Test
  void retainsFailedAndInvalidObservationsWithoutSummarizing() throws Exception {
    AgentStartupReport report = new AgentStartupReport(directory);
    var failed = new AgentStartupReport.Sample(0, false, "failed", Double.NaN, 0);
    report.append(failed);
    assertThatThrownBy(() -> report.summarize(List.of(failed)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(Files.readString(directory.resolve("samples.csv"))).contains("false,failed,NaN,0.0");
    assertThat(directory.resolve("summary.md")).doesNotExist();

    var invalid = new AgentStartupReport.Sample(1, false, "ok", Double.NaN, 0);
    report.append(invalid);
    assertThatThrownBy(() -> report.summarize(List.of(invalid)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(Files.readString(directory.resolve("samples.csv"))).contains("1,false,ok,NaN,0.0");
  }
}
