/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.e2ebenchmark;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

public class AgentBenchmark {
  private static final String APP_NAME =
      System.getenv()
          .getOrDefault(
              "APP_IMAGE",
              "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-spring-boot:jdk8-20210918.1248928124");

  private List<GenericContainer<?>> containers;
  private static final Logger logger = LoggerFactory.getLogger(AgentBenchmark.class);

  // docker images
  private static final DockerImageName APP_IMAGE = DockerImageName.parse(APP_NAME);
  private static final DockerImageName OTLP_COLLECTOR_IMAGE =
      DockerImageName.parse("otel/opentelemetry-collector-dev:latest");
  private static final DockerImageName WRK_IMAGE = DockerImageName.parse("quay.io/dim/wrk:stable");

  @BeforeEach
  void setUp() {
    containers = new ArrayList<>();
  }

  @AfterEach
  void tearDown() {
    containers.forEach(GenericContainer::stop);
  }

  @Test
  void run() throws InterruptedException {
    runBenchmark();
  }

  private void runBenchmark() throws InterruptedException {
    String agentPath = System.getProperty("io.opentelemetry.smoketest.agent.shadowJar.path");

    // otlp collector container
    GenericContainer<?> collector =
        new GenericContainer<>(OTLP_COLLECTOR_IMAGE)
            .withNetwork(Network.SHARED)
            .withNetworkAliases("collector")
            .withLogConsumer(new Slf4jLogConsumer(logger))
            .withExposedPorts(4317, 13133)
            .waitingFor(Wait.forHttp("/").forPort(13133))
            .withCopyFileToContainer(
                MountableFile.forClasspathResource("collector-config.yml"),
                "/etc/collector/collector-config.yml")
            .withCommand("--config /etc/collector/collector-config.yml --log-level=DEBUG");
    containers.add(collector);

    // sample app container
    GenericContainer<?> app =
        new GenericContainer<>(APP_IMAGE)
            .withNetwork(Network.SHARED)
            .withLogConsumer(new Slf4jLogConsumer(logger))
            .withNetworkAliases("app")
            .withCopyFileToContainer(
                MountableFile.forHostPath(agentPath), "/opentelemetry-javaagent-all.jar")
            .withEnv("OTEL_EXPORTER_OTLP_ENDPOINT", "collector:4317")
            .withEnv("JAVA_TOOL_OPTIONS", "-javaagent:/opentelemetry-javaagent-all.jar")
            .withExposedPorts(8080);
    containers.add(app);

    // wrk benchmark container
    GenericContainer<?> wrk =
        new GenericContainer<>(WRK_IMAGE)
            .withNetwork(Network.SHARED)
            .withLogConsumer(new Slf4jLogConsumer(logger))
            .withCreateContainerCmdModifier(it -> it.withEntrypoint("wrk"))
            .withCommand("-t4 -c128 -d300s http://app:8080/ --latency");
    containers.add(wrk);

    wrk.dependsOn(app, collector);
    Startables.deepStart(Stream.of(wrk)).join();

    logger.info("Benchmark started");
    printContainerMapping(collector);
    printContainerMapping(app);

    while (wrk.isRunning()) {
      Thread.sleep(1000);
    }

    Thread.sleep(5000);
    logger.info("Benchmark complete, wrk output:");
    logger.info(wrk.getLogs().replace("\n\n", "\n"));
  }

  static void printContainerMapping(GenericContainer<?> container) {
    logger.info(
        "Container {} ports exposed at {}",
        container.getDockerImageName(),
        container.getExposedPorts().stream()
            .map(
                port ->
                    new AbstractMap.SimpleImmutableEntry<>(
                        port,
                        "http://"
                            + container.getContainerIpAddress()
                            + ":"
                            + container.getMappedPort(port)))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
  }
}
