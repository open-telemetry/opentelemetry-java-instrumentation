/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.containers;

import com.jayway.jsonpath.JsonPath;
import io.opentelemetry.agents.Agent;
import io.opentelemetry.agents.AgentResolver;
import io.opentelemetry.config.TestConfig.WebFluxMode;
import io.opentelemetry.util.NamingConventions;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

public final class WebFluxContainer {
  private static final int PORT = 8080;
  private static final String IMAGE =
      "eclipse-temurin:25-jdk@sha256:ce2ef9d2d67a68de5e196d4de9d743327c44a10b57fe02ec39f572c652cacfd3";

  public static GenericContainer<?> build(
      Network network, Agent agent, WebFluxMode mode, Path appJar, NamingConventions names)
      throws Exception {
    var agentJar = new AgentResolver().resolve(agent);
    var javaArgs =
        new ArrayList<>(
            List.of(
                "-Xms1g",
                "-Xmx1g",
                "-XX:ActiveProcessorCount=8",
                "-Dbenchmark.reactor.automatic=" + mode.automatic(),
                "-Dotel.instrumentation.reactor.enabled=" + mode.reactor(),
                "-Dotel.traces.exporter=otlp",
                "-Dotel.metrics.exporter=none",
                "-Dotel.logs.exporter=none",
                "-Dotel.exporter.otlp.protocol=http/protobuf",
                "-Dotel.exporter.otlp.endpoint=http://collector:4318",
                "-Dotel.resource.attributes=service.name=webflux-load-benchmark"));
    agentJar.ifPresent(path -> javaArgs.add("-javaagent:/app/agent.jar"));
    javaArgs.addAll(List.of("-jar", "/app/webflux-benchmark.jar"));

    var container =
        new GenericContainer<>(DockerImageName.parse(IMAGE))
            .withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("java"))
            .withCommand(javaArgs.toArray(String[]::new))
            .withNetwork(network)
            .withNetworkAliases("webflux")
            .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger(WebFluxContainer.class)))
            .withExposedPorts(PORT)
            .withFileSystemBind(names.localResults(), names.containerResults())
            .withCopyFileToContainer(
                MountableFile.forHostPath(appJar), "/app/webflux-benchmark.jar")
            .withCopyFileToContainer(
                MountableFile.forClasspathResource("overhead.jfc"), "/app/overhead.jfc")
            .withEnv(
                "SPRING_R2DBC_URL",
                "r2dbc:postgresql://postgres:5432/" + PostgresContainer.DATABASE_NAME)
            .withEnv("SPRING_R2DBC_USERNAME", PostgresContainer.USERNAME)
            .withEnv("SPRING_R2DBC_PASSWORD", PostgresContainer.PASSWORD)
            .waitingFor(
                Wait.forHttp("/probe").forPort(PORT).withStartupTimeout(Duration.ofMinutes(2)));
    agentJar.ifPresent(path ->
        container.withCopyFileToContainer(MountableFile.forHostPath(path), "/app/agent.jar"));
    return container;
  }

  public static void verify(
      GenericContainer<?> container, Agent agent, WebFluxMode mode, NamingConventions names)
      throws Exception {
    var uri =
        URI.create(
            "http://" + container.getHost() + ":" + container.getMappedPort(PORT) + "/probe");
    try (var client = HttpClient.newHttpClient()) {
      var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).build();
      var response = client.send(request, HttpResponse.BodyHandlers.ofString());
      Files.writeString(
          Path.of(names.localResults(), "probe-" + agent.getName() + ".json"), response.body());
      if (response.statusCode() != 200) {
        throw new IllegalStateException("WebFlux probe returned HTTP " + response.statusCode());
      }
      var probe = JsonPath.parse(response.body());
      String threadLocal = probe.read("$.threadLocal");
      boolean agentHook = probe.read("$.agentHook");
      if (!"George".equals(probe.read("$.owner.firstName"))
          || !"Franklin".equals(probe.read("$.owner.lastName"))
          || !"probe".equals(probe.read("$.requestId"))
          || !Objects.equals(threadLocal, mode.automatic() ? "probe" : null)
          || agentHook != mode.reactor()) {
        throw new IllegalStateException(
            "Wrong WebFlux fixture or effective Reactor mode for "
                + agent.getName()
                + ": "
                + response.body());
      }
    }
  }

  private WebFluxContainer() {}
}
