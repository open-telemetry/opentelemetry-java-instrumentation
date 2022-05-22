/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.containers;

import io.opentelemetry.agents.Agent;
import io.opentelemetry.config.TestConfig;
import io.opentelemetry.util.NamingConventions;
import java.nio.file.Path;
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
    Path k6OutputFile = namingConventions.container.k6Results(agent);
    return new GenericContainer<>(DockerImageName.parse("loadimpact/k6"))
        .withNetwork(network)
        .withNetworkAliases("k6")
        .withLogConsumer(new Slf4jLogConsumer(logger))
        .withCopyFileToContainer(MountableFile.forHostPath("./k6"), "/app")
        .withFileSystemBind(namingConventions.localResults(), namingConventions.containerResults())
        .withCreateContainerCmdModifier(cmd -> cmd.withUser("root"))
        .withCommand(
            "run",
            "-u",
            String.valueOf(config.getConcurrentConnections()),
            "-i",
            String.valueOf(config.getTotalIterations()),
            "--rps",
            String.valueOf(config.getMaxRequestRate()),
            "--summary-export",
            k6OutputFile.toString(),
            "/app/basic.js")
        .withStartupCheckStrategy(
            new OneShotStartupCheckStrategy().withTimeout(Duration.ofMinutes(15)));
  }
}
