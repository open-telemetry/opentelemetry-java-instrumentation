/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import io.opentelemetry.agents.Agent;
import io.opentelemetry.config.Configs;
import io.opentelemetry.config.TestConfig;
import io.opentelemetry.containers.CollectorContainer;
import io.opentelemetry.containers.K6Container;
import io.opentelemetry.containers.PetClinicRestContainer;
import io.opentelemetry.containers.PostgresContainer;
import io.opentelemetry.containers.WebFluxContainer;
import io.opentelemetry.results.AppPerfResults;
import io.opentelemetry.results.LoadBenchmarkReport;
import io.opentelemetry.results.MainResultsPersister;
import io.opentelemetry.results.ResultsCollector;
import io.opentelemetry.results.WebFluxLoadReport;
import io.opentelemetry.util.NamingConventions;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

public class OverheadTests {

  private static final Network NETWORK = Network.newNetwork();
  private static final String AGENT_DIR = System.getProperty("load.benchmark.agentDir");
  private static final Path REPORTS = Path.of("build/reports/load-benchmark");
  private static GenericContainer<?> collector;

  @BeforeAll
  static void setUp() {
    collector = CollectorContainer.build(NETWORK);
    collector.start();
  }

  @AfterAll
  static void tearDown() {
    try {
      if (collector != null) {
        collector.close();
      }
    } finally {
      NETWORK.close();
    }
  }

  @TestFactory
  Stream<DynamicTest> runAllTestConfigurations() {
    Stream<TestConfig> configs =
        AGENT_DIR == null
            ? Configs.all()
            : switch (System.getProperty("load.benchmark.workload")) {
              case "petclinic" -> Stream.of(Configs.manualPetclinic(Path.of(AGENT_DIR)));
              case "webflux" -> Configs.manualWebFlux(Path.of(AGENT_DIR));
              default ->
                  throw new IllegalArgumentException("Expected Petclinic or WebFlux workload");
            };
    return configs.map(config -> dynamicTest(config.getName(), () -> runTestConfig(config)));
  }

  void runTestConfig(TestConfig config) throws IOException {
    Path output =
        config.isWebFlux() ? REPORTS.resolve("webflux").resolve(config.getName()) : REPORTS;
    if (config.isWebFlux()) {
      Files.createDirectories(output);
    }
    NamingConventions names =
        config.isWebFlux()
            ? new NamingConventions(output.toAbsolutePath().toString(), "webflux")
            : new NamingConventions();
    Map<String, Long> runDurations = new HashMap<>();
    config
        .getAgents()
        .forEach(agent -> {
          try {
            runDurations.put(agent.getName(), runAppOnce(config, agent, names));
          } catch (Exception e) {
            fail("Unhandled exception in " + config.getName(), e);
          }
        });
    if (config.isWebFlux()) {
      WebFluxLoadReport.write(output.resolve("summary.md"), config, names.local);
    } else {
      List<AppPerfResults> results =
          new ResultsCollector(names.local, runDurations).collect(config, AGENT_DIR != null);
      if (AGENT_DIR == null) {
        new MainResultsPersister(config).write(results);
      } else {
        LoadBenchmarkReport.write(output.resolve("summary.md"), config, results);
      }
    }
  }

  long runAppOnce(TestConfig config, Agent agent, NamingConventions names) throws Exception {
    try (GenericContainer<?> postgres = new PostgresContainer(NETWORK).build()) {
      postgres.start();

      try (GenericContainer<?> app =
          config.isWebFlux()
              ? WebFluxContainer.build(
                  NETWORK,
                  agent,
                  config.webFluxMode(agent),
                  Path.of(System.getProperty("load.benchmark.webfluxJar")),
                  names)
              : new PetClinicRestContainer(NETWORK, collector, agent, names).build()) {
        long start = System.currentTimeMillis();
        app.start();
        writeStartupTimeFile(agent, start, names);
        if (config.isWebFlux()) {
          WebFluxContainer.verify(app, agent, config.webFluxMode(agent), names);
        }

        K6Container load = new K6Container(NETWORK, agent, config, names);
        if (config.getWarmupSeconds() > 0) {
          doWarmupPhase(config, app, load);
        }

        long testStart = System.currentTimeMillis();
        startRecording(agent, app, names, config.isWebFlux() ? "webflux" : "petclinic");

        try (GenericContainer<?> k6 = load.build()) {
          try {
            k6.start();
            return System.currentTimeMillis() - testStart;
          } finally {
            // Shut down the JVM gracefully so it writes the measuring JFR recording.
            var result = app.execInContainer("kill", "1");
            if (result.getExitCode() != 0) {
              throw new IllegalStateException("Could not stop benchmark app: " + result);
            }
            while (app.isRunning()) {
              MILLISECONDS.sleep(500);
            }
          }
        }
      }
    }
  }

  private void startRecording(
      Agent agent, GenericContainer<?> app, NamingConventions names, String recording)
      throws Exception {
    String outFile = names.container.jfrFile(agent);
    String[] command = {
      "jcmd",
      "1",
      "JFR.start",
      "settings=/app/overhead.jfc",
      "dumponexit=true",
      "name=" + recording,
      "filename=" + outFile
    };
    var result = app.execInContainer(command);
    if (result.getExitCode() != 0) {
      throw new IllegalStateException("Could not start JFR: " + result);
    }
  }

  private void doWarmupPhase(TestConfig testConfig, GenericContainer<?> app, K6Container load)
      throws IOException, InterruptedException {
    System.out.println(
        "Performing startup warming phase for " + testConfig.getWarmupSeconds() + " seconds...");

    // excluding the JFR recording from the warmup causes strange inconsistencies in the results
    System.out.println("Starting disposable JFR warmup recording...");
    String[] startCommand = {
      "jcmd",
      "1",
      "JFR.start",
      "settings=/app/overhead.jfc",
      "dumponexit=true",
      "name=warmup",
      "filename=warmup.jfr"
    };
    var startResult = app.execInContainer(startCommand);
    if (startResult.getExitCode() != 0) {
      throw new IllegalStateException("Could not start warmup JFR: " + startResult);
    }

    if (testConfig.isWebFlux()) {
      try (GenericContainer<?> k6 = load.webFlux(testConfig.getWarmupSeconds())) {
        k6.start();
      }
    } else {
      long deadline = System.currentTimeMillis() + SECONDS.toMillis(testConfig.getWarmupSeconds());
      while (System.currentTimeMillis() < deadline) {
        try (GenericContainer<?> k6 =
            new GenericContainer<>(DockerImageName.parse("grafana/k6"))
                .withNetwork(NETWORK)
                .withCopyFileToContainer(MountableFile.forHostPath("./k6"), "/app")
                .withCommand("run", "-u", "5", "-i", "200", "/app/basic.js")
                .withStartupCheckStrategy(
                    new OneShotStartupCheckStrategy().withTimeout(Duration.ofMinutes(5)))) {
          k6.start();
        }
      }
    }

    System.out.println("Stopping disposable JFR warmup recording...");
    String[] stopCommand = {"jcmd", "1", "JFR.stop", "name=warmup"};
    var stopResult = app.execInContainer(stopCommand);
    if (stopResult.getExitCode() != 0) {
      throw new IllegalStateException("Could not stop warmup JFR: " + stopResult);
    }

    System.out.println("Warmup complete.");
  }

  private void writeStartupTimeFile(Agent agent, long start, NamingConventions names)
      throws IOException {
    long delta = System.currentTimeMillis() - start;
    Path startupPath = Path.of(names.local.startupDurationFile(agent));
    Files.writeString(startupPath, String.valueOf(delta));
  }
}
