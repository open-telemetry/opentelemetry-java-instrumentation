/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.opentelemetry.containers;

import io.opentelemetry.util.NamingConvention;
import io.opentelemetry.agents.Agent;
import io.opentelemetry.config.TestConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import java.nio.file.Path;
import java.time.Duration;

public class K6Container {
  private static final Logger logger = LoggerFactory.getLogger(K6Container.class);
  private final Network network;
  private final Agent agent;
  private final TestConfig config;
  private final NamingConvention namingConvention;

  public K6Container(Network network, Agent agent, TestConfig config, NamingConvention namingConvention) {
    this.network = network;
    this.agent = agent;
    this.config = config;
    this.namingConvention = namingConvention;
  }

  public GenericContainer<?> build(){
    Path k6OutputFile = namingConvention.k6Results(agent);
    return new GenericContainer<>(
        DockerImageName.parse("loadimpact/k6"))
        .withNetwork(network)
        .withNetworkAliases("k6")
        .withLogConsumer(new Slf4jLogConsumer(logger))
        .withCopyFileToContainer(
            MountableFile.forHostPath("./k6"), "/app")
        .withFileSystemBind(".", "/results")
        .withCommand(
            "run",
            "-u", String.valueOf(config.getConcurrentConnections()),
            "-i", String.valueOf(config.getTotalIterations()),
            "--rps", String.valueOf(config.getMaxRequestRate()),
            "--summary-export", k6OutputFile.toString(),
            "/app/basic.js"
        )
        .withStartupCheckStrategy(
            new OneShotStartupCheckStrategy().withTimeout(Duration.ofMinutes(5))
        );
  }
}
