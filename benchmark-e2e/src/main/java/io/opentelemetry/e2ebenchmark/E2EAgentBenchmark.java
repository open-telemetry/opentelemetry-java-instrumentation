package io.opentelemetry.e2ebenchmark;

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

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class E2EAgentBenchmark {
  private static final Logger LOG = LoggerFactory.getLogger(E2EAgentBenchmark.class);
  private List<GenericContainer<?>> containers;

  @BeforeEach
  void setUp() {
    containers = new ArrayList<>();
  }

  @AfterEach
  void tearDown() {
    containers.forEach(GenericContainer::stop);
  }

  @Test
  void run() throws Exception {
    runBenchmark();
  }

  private void runBenchmark() throws Exception {
    String agentPath = System.getProperty("io.opentelemetry.smoketest.agent.shadowJar.path");

    // docker images
    final DockerImageName WRK_IMAGE = DockerImageName.parse("quay.io/dim/wrk:stable");
    final DockerImageName APP_IMAGE = DockerImageName.parse("ghcr.io/open-telemetry/java-test-containers:smoke-springboot-jdk8-20201204.400701583");
    final DockerImageName OTLP_COLLECTOR_IMAGE = DockerImageName.parse("otel/opentelemetry-collector-dev:latest");

    // otlp collector container
    GenericContainer<?> collector =
        new GenericContainer<>(OTLP_COLLECTOR_IMAGE)
            .withNetwork(Network.SHARED)
            .withNetworkAliases("collector")
            .withLogConsumer(new Slf4jLogConsumer(LOG))
            .withExposedPorts(55680, 13133)
            .waitingFor(Wait.forHttp("/").forPort(13133))
            .withCopyFileToContainer(
                MountableFile.forClasspathResource("collector-config.yml"), "/etc/collector/collector-config.yml")
            .withCommand("--config /etc/collector/collector-config.yml --log-level=DEBUG");
    containers.add(collector);

    // sample app container
    GenericContainer<?> app = new GenericContainer<>(APP_IMAGE)
        .withNetwork(Network.SHARED)
        .withLogConsumer(new Slf4jLogConsumer(LOG))
        .withNetworkAliases("app")
        .withCopyFileToContainer(MountableFile.forHostPath(agentPath), "/opentelemetry-javaagent-all.jar")
        .withEnv("JAVA_TOOL_OPTIONS", "-javaagent:/opentelemetry-javaagent-all.jar")
        .withEnv("OTEL_EXPORTER_OTLP_ENDPOINT", "collector:55680")
        .withExposedPorts(8080);
    containers.add(app);

    // wrk benchmark container
    GenericContainer<?> wrk = new GenericContainer<>(WRK_IMAGE)
        .withNetwork(Network.SHARED)
        .withLogConsumer(new Slf4jLogConsumer(LOG))
        .withCreateContainerCmdModifier(it -> it.withEntrypoint("wrk"))
        .withCommand("-t4 -c128 -d300s http://app:8080/ --latency");
    containers.add(wrk);

    wrk.dependsOn(app, collector);
    Startables.deepStart(Stream.of(wrk)).join();

    LOG.info("Benchmark started");
    printContainerMapping(collector);
    printContainerMapping(app);

    while (wrk.isRunning()) {
      Thread.sleep(1000);
    }

    Thread.sleep(5000);
    LOG.info("Benchmark complete, wrk output:");
    LOG.info(wrk.getLogs().replace("\n\n", "\n"));
  }

  static void printContainerMapping(GenericContainer<?> container) {
    System.out.println(String.format(
        "Container %s ports exposed at %s",
        container.getDockerImageName(),
        container.getExposedPorts().stream()
            .map(port -> new AbstractMap.SimpleImmutableEntry<>(port,
                "http://" + container.getContainerIpAddress() + ":" + container
                    .getMappedPort(port)))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))));
  }
}
