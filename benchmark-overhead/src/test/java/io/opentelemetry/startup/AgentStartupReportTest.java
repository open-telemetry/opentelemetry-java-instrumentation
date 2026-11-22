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
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.utility.DockerImageName;

class AgentStartupReportTest {
  @TempDir Path directory;

  @Test
  void launchesOnlyTheSuppliedAgentAndKeepsProfilingSeparate() {
    assertThat(AgentStartupBenchmark.arguments(true, false))
        .contains("-Xmx512m", "-javaagent:/tmp/agent.jar")
        .noneMatch(value ->
            value.contains("bootclasspath")
                || value.contains("AOT")
                || value.contains("FlightRecord")
                || value.contains("startup-recorder")
                || value.contains("otel.sdk.disabled")
                || value.contains("otel.instrumentation"));
    assertThat(AgentStartupBenchmark.arguments(true, true))
        .contains(
            "-XX:FlightRecorderOptions=stackdepth=256",
            "-javaagent:/tmp/agent.jar",
            "-javaagent:/tmp/startup-recorder.jar=/tmp/startup.jfr");
    assertThat(AgentStartupBenchmark.arguments(true, true))
        .containsSubsequence(
            "-javaagent:/tmp/startup-recorder.jar=/tmp/startup.jfr", "-javaagent:/tmp/agent.jar");
  }

  @Test
  void noAgentControlUsesTheSameLaunchRecipeWithoutAnyAgent() {
    assertThat(AgentStartupBenchmark.arguments(false, false))
        .containsExactly(
            "-Xmx512m",
            "-cp",
            "/app/resources:/app/classes:/app/libs/*",
            StartupFixture.APPLICATION);
  }

  @Test
  void petclinicLaunchUsesTheSameApplicationClasspathForEachConfiguration() {
    var control = AgentStartupBenchmark.arguments(false, false, true);
    var agent = AgentStartupBenchmark.arguments(true, false, true);
    assertThat(control)
        .containsExactly(
            "-Xmx512m",
            "-Dloader.path=/app/annotations,/app/startup-helper",
            "-cp",
            "/app/spring-petclinic-rest.jar",
            "org.springframework.boot.loader.PropertiesLauncher");
    assertThat(agent)
        .containsExactly(
            "-Xmx512m",
            "-javaagent:/tmp/agent.jar",
            "-Dloader.path=/app/annotations,/app/startup-helper",
            "-cp",
            "/app/spring-petclinic-rest.jar",
            "org.springframework.boot.loader.PropertiesLauncher");
    assertThat(AgentStartupBenchmark.arguments(false, true, true))
        .contains("-Dloader.path=/app/annotations,/app/startup-helper");
  }

  @Test
  void petclinicReadinessRequiresTheSeededOwner() {
    PetclinicStartupFixture.validateResponse(
        200, "{\"id\":1,\"firstName\":\"StartupBenchmark\",\"lastName\":\"Franklin\"}");
    assertThatThrownBy(() ->
            PetclinicStartupFixture.validateResponse(
                200, "{\"id\":1,\"firstName\":\"Other\",\"lastName\":\"Franklin\"}"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> PetclinicStartupFixture.validateResponse(200, "{}"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() ->
            PetclinicStartupFixture.validateResponse(
                503, "{\"id\":1,\"firstName\":\"StartupBenchmark\",\"lastName\":\"Franklin\"}"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void petclinicValidatesInputsBeforeStartingDocker() throws Exception {
    assertThat(
            DockerImageName.parse(PetclinicStartupFixture.DATABASE_IMAGE)
                .isCompatibleWith(DockerImageName.parse("postgres")))
        .isTrue();
    Path missing = directory.resolve("missing.jar");
    Path missingHelper = directory.resolve("missing-helper.jar");
    assertThatThrownBy(() -> PetclinicStartupFixture.start(missing, missingHelper))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Annotation JAR does not exist");
    Path invalid = directory.resolve("invalid.jar");
    new JarOutputStream(Files.newOutputStream(invalid)).close();
    assertThatThrownBy(() -> PetclinicStartupFixture.start(invalid, missingHelper))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid annotation JAR");
    Path validAnnotations = directory.resolve("annotations.jar");
    try (var jar = new JarOutputStream(Files.newOutputStream(validAnnotations))) {
      jar.putNextEntry(
          new java.util.jar.JarEntry(
              "io/opentelemetry/instrumentation/annotations/WithSpan.class"));
      jar.closeEntry();
    }
    assertThatThrownBy(() -> PetclinicStartupFixture.start(validAnnotations, missingHelper))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Petclinic startup helper JAR does not exist");
    assertThatThrownBy(() -> PetclinicStartupFixture.start(validAnnotations, invalid))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid Petclinic startup helper JAR");
  }

  @Test
  void petclinicSummaryUsesTheCommonJvmUptimeBoundary() throws Exception {
    AgentStartupReport report = new AgentStartupReport(directory);
    report.summarize(List.of(new AgentStartupReport.Sample("no-agent", 0, false, "ok", 1, 0)));
    assertThat(Files.readString(directory.resolve("summary.md")))
        .contains(
            "JVM uptime sampled inside the application",
            "return network transit",
            "Probe polling cadence");
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
  void readinessResultIncludesPositiveUptimeAndJvmVersion() {
    var smoke = AgentStartupBenchmark.readinessResult(200, "Hi!", "1250", "17.0.7+7", false);
    assertThat(smoke.uptimeSeconds()).isEqualTo(1.25);
    assertThat(smoke.jvmVersion()).isEqualTo("17.0.7+7");

    var petclinic =
        AgentStartupBenchmark.readinessResult(
            200,
            "{\"id\":1,\"firstName\":\"StartupBenchmark\",\"lastName\":\"Franklin\"}",
            "2500",
            "17.0.7+7",
            true);
    assertThat(petclinic.uptimeSeconds()).isEqualTo(2.5);
    assertThat(petclinic.jvmVersion()).isEqualTo("17.0.7+7");

    assertThatThrownBy(() -> AgentStartupBenchmark.readinessResult(200, "Hi!", "1250", " ", false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Missing application JVM version response header");
  }

  @Test
  void petclinicRequiresTheValidatedResponseAndPositiveApplicationUptime() {
    assertThat(
            AgentStartupBenchmark.petclinicStartupSeconds(
                200,
                "{\"id\":1,\"firstName\":\"StartupBenchmark\",\"lastName\":\"Franklin\"}",
                "1250"))
        .isEqualTo(1.25);
    assertThatThrownBy(() ->
            AgentStartupBenchmark.petclinicStartupSeconds(
                200, "{\"id\":1,\"firstName\":\"Other\",\"lastName\":\"Franklin\"}", "1250"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() ->
            AgentStartupBenchmark.petclinicStartupSeconds(
                200,
                "{\"id\":1,\"firstName\":\"StartupBenchmark\",\"lastName\":\"Franklin\"}",
                null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Missing application JVM uptime response header");
    assertThatThrownBy(() ->
            AgentStartupBenchmark.petclinicStartupSeconds(
                200,
                "{\"id\":1,\"firstName\":\"StartupBenchmark\",\"lastName\":\"Franklin\"}",
                "0"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Application JVM uptime must be positive");
  }

  @Test
  void retainsWarmupsButExcludesThemFromSingleSampleDistributions() throws Exception {
    AgentStartupReport report = new AgentStartupReport(directory);
    var warmup = new AgentStartupReport.Sample("agent", 0, true, "ok", 3, 0);
    var control = new AgentStartupReport.Sample("no-agent", 1, false, "ok", .5, 0);
    var measured = new AgentStartupReport.Sample("agent", 1, false, "ok", 2, 0);
    List<AgentStartupReport.Sample> samples = List.of(warmup, control, measured);
    report.append(warmup);
    report.append(control);
    report.append(measured);
    report.summarize(samples);
    assertThat(Files.readString(directory.resolve("summary.md")))
        .contains(
            "| no-agent | 1 | 500.0 | 500.0 | 500.0 | 500.0 | 0.0 |",
            "| agent | 1 | 2000.0 | 2000.0 | 2000.0 | 2000.0 | 1500.0 |")
        .doesNotContain("Spring startup", "HTTP readiness")
        .doesNotContain("baseline-agent", "Agent-added overhead reduction:");
    assertThat(Files.readAllLines(directory.resolve("samples.csv")))
        .containsExactly(
            "configuration,round,discarded,status,startup_seconds,host_load_average",
            "agent,0,true,ok,3.0,0.0",
            "no-agent,1,false,ok,0.5,0.0",
            "agent,1,false,ok,2.0,0.0");
  }

  @Test
  void comparesAddedOverheadUsingTheNoAgentMedian() throws Exception {
    AgentStartupReport report = new AgentStartupReport(directory);
    report.summarize(
        List.of(
            new AgentStartupReport.Sample("no-agent", 0, false, "ok", 1, 0),
            new AgentStartupReport.Sample("baseline-agent", 0, false, "ok", 4, 0),
            new AgentStartupReport.Sample("agent", 0, false, "ok", 3, 0),
            new AgentStartupReport.Sample("no-agent", 1, false, "ok", 3, 0),
            new AgentStartupReport.Sample("baseline-agent", 1, false, "ok", 6, 0),
            new AgentStartupReport.Sample("agent", 1, false, "ok", 5, 0)));
    assertThat(Files.readString(directory.resolve("summary.md")))
        .contains(
            "| no-agent | 2 | 1500.0 | 2000.0 | 2500.0 | 2800.0 | 0.0 |",
            "| baseline-agent | 2 | 4500.0 | 5000.0 | 5500.0 | 5800.0 | 3000.0 |",
            "| agent | 2 | 3500.0 | 4000.0 | 4500.0 | 4800.0 | 2000.0 |",
            "Agent-added overhead reduction: 1000.0 ms, 33.33% of baseline agent-added overhead.");
  }

  @Test
  void doesNotReportAPercentageWhenBaselineOverheadIsNotPositive() throws Exception {
    AgentStartupReport report = new AgentStartupReport(directory);
    report.summarize(
        List.of(
            new AgentStartupReport.Sample("no-agent", 0, false, "ok", 2, 0),
            new AgentStartupReport.Sample("baseline-agent", 0, false, "ok", 1, 0),
            new AgentStartupReport.Sample("agent", 0, false, "ok", 1.5, 0)));
    assertThat(Files.readString(directory.resolve("summary.md")))
        .contains(
            "Agent-added overhead reduction: -500.0 ms.",
            "Relative reduction is undefined because measured baseline overhead is not positive.")
        .doesNotContain("% of baseline");
  }

  @Test
  void requiresAMeasuredNoAgentControl() {
    AgentStartupReport report = new AgentStartupReport(directory);
    assertThatThrownBy(() ->
            report.summarize(List.of(new AgentStartupReport.Sample("agent", 0, false, "ok", 1, 0))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("A measured no-agent control is required");
  }

  @Test
  void retainsFailedAndInvalidObservationsWithoutSummarizing() throws Exception {
    AgentStartupReport report = new AgentStartupReport(directory);
    var failed = new AgentStartupReport.Sample("agent", 0, false, "failed", Double.NaN, 0);
    report.append(failed);
    assertThatThrownBy(() -> report.summarize(List.of(failed)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(Files.readString(directory.resolve("samples.csv"))).contains("false,failed,NaN,0.0");
    assertThat(directory.resolve("summary.md")).doesNotExist();

    var invalid = new AgentStartupReport.Sample("agent", 1, false, "ok", Double.NaN, 0);
    report.append(invalid);
    assertThatThrownBy(() -> report.summarize(List.of(invalid)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(Files.readString(directory.resolve("samples.csv"))).contains("1,false,ok,NaN,0.0");
  }
}
