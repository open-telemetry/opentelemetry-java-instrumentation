/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.containers;

import io.opentelemetry.agents.Agent;
import io.opentelemetry.config.TestConfig;
import io.opentelemetry.util.NamingConventions;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

public class K6Container {
  public static final String WEBFLUX_IMAGE =
      "grafana/k6@sha256:a5ad6bc089a08d77c3ec49f3db8c6fa7a148e4073efcac44c675dbaf3568d8e1";
  public static final int WEBFLUX_RATE = 600;
  public static final int WEBFLUX_SECONDS = 120;

  private static final Logger logger = LoggerFactory.getLogger(K6Container.class);
  private final Network network;
  private final Agent agent;
  private final TestConfig config;
  private final NamingConventions namingConventions;

  public K6Container(
      Network network, Agent agent, TestConfig config, NamingConventions namingConvention) {
    this.network = network;
    this.agent = agent;
    this.config = config;
    this.namingConventions = namingConvention;
  }

  public GenericContainer<?> build() {
    String k6OutputFile = namingConventions.container.k6Results(agent);
    if (config.isWebFlux()) {
      return webFlux(WEBFLUX_SECONDS).withEnv("BENCHMARK_SUMMARY", k6OutputFile);
    }
    return container("grafana/k6")
        .withCommand(
            "run",
            "-u",
            String.valueOf(config.getConcurrentConnections()),
            "-i",
            String.valueOf(config.getTotalIterations()),
            "--rps",
            String.valueOf(config.getMaxRequestRate()),
            "--summary-export",
            k6OutputFile,
            "/app/basic.js");
  }

  public GenericContainer<?> webFlux(int seconds) {
    return container(WEBFLUX_IMAGE)
        .withEnv("BENCHMARK_RATE", String.valueOf(WEBFLUX_RATE))
        .withEnv("BENCHMARK_SECONDS", String.valueOf(seconds))
        .withCommand("run", "/app/webflux.js");
  }

  private GenericContainer<?> container(String image) {
    return new GenericContainer<>(DockerImageName.parse(image))
        .withNetwork(network)
        .withNetworkAliases("k6")
        .withLogConsumer(new Slf4jLogConsumer(logger))
        .withCopyFileToContainer(MountableFile.forHostPath("./k6"), "/app")
        .withFileSystemBind(namingConventions.localResults(), namingConventions.containerResults())
        .withCreateContainerCmdModifier(cmd -> cmd.withUser("root"))
        .withStartupCheckStrategy(
            new OneShotStartupCheckStrategy().withTimeout(Duration.ofMinutes(15)));
  }
}
