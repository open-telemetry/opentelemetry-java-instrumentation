/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.opentelemetry;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.lifecycle.Startable;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

public class PetClinicRestContainer {

  private static final Logger logger = LoggerFactory.getLogger(PetClinicRestContainer.class);
  private static final int PETCLINIC_PORT = 9966;
  private String agentName;
  private Optional<Path> agent;
  private Network network;
  private Startable collector;

  private GenericContainer<?> build() {

    GenericContainer<?> container = new GenericContainer<>(
//        DockerImageName.parse("ghcr.io/open-telemetry/opentelemetry-java-instrumentation/petclinic-base:latest"))
        DockerImageName.parse("temp-petclinic-base")) // Temp hack until we get the base image published
        .withNetwork(network)
        .withNetworkAliases("petclinic")
        .withLogConsumer(new Slf4jLogConsumer(logger))
        .withExposedPorts(PETCLINIC_PORT)
        .withFileSystemBind(".", "/results")
        .waitingFor(Wait.forHttp("/petclinic/actuator/health").forPort(PETCLINIC_PORT))
        .dependsOn(collector)
        .withCommand(buildCommandline());

    agent.ifPresent(
        agentPath -> container.withCopyFileToContainer(
            MountableFile.forHostPath(agentPath),
            "/app/" + agentPath.getFileName().toString())
    );
    return container;
  }

  @NotNull
  private String[] buildCommandline() {
    String jfrFile = "petclinic-" + agentName + ".jfr";
    List<String> result = new ArrayList<>(Arrays.asList(
        "java",
        "-XX:StartFlightRecording:dumponexit=true,disk=true,settings=profile,name=petclinic,filename=/results/" + jfrFile,
        "-Dotel.traces.exporter=otlp",
        "-Dotel.imr.export.interval=5000",
        "-Dotel.exporter.otlp.insecure=true",
        "-Dotel.exporter.otlp.endpoint=http://collector:4317",
        "-Dotel.resource.attributes=service.name=petclinic-otel-overhead"
    ));
    agent.ifPresent(path -> result.add("-javaagent:/app/" + path.getFileName()));

    result.add("-jar");
    result.add("/app/spring-petclinic-rest.jar");
    return result.toArray(new String[] {});
  }

  static Builder builder() {
    return new Builder();
  }

  static class Builder {

    private final AgentResolver agentResolver = new AgentResolver();
    private Network network;
    private Startable collector;
    private String agent;

    Builder withNetwork(Network network) {
      this.network = network;
      return this;
    }

    Builder withCollectorContainer(Startable collector) {
      this.collector = collector;
      return this;
    }

    Builder withAgentNamed(String agentName) {
      this.agent = agentName;
      return this;
    }

    GenericContainer<?> build() throws IOException {
      PetClinicRestContainer container = new PetClinicRestContainer();
      container.network = network;
      container.collector = collector;
      container.agentName = agent;
      container.agent = agentResolver.resolve(agent);
      return container.build();
    }
  }

}
