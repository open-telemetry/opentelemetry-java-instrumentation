/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.opentelemetry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import java.time.Duration;

class K6Container {
  private static final Logger logger = LoggerFactory.getLogger(K6Container.class);

  private K6Container() {
  }

  static Builder builder() {
    return new Builder();
  }

  static class Builder {

    private Network network;
    private String agent;
    private TestConfig config;

    Builder withNetwork(Network network) {
      this.network = network;
      return this;
    }

    Builder withConfig(TestConfig config) {
      this.config = config;
      return this;
    }

    Builder withAgentNamed(String agent) {
      this.agent = agent;
      return this;
    }

    GenericContainer<?> build(){
      String k6OutputFile = "/results/k6_out_" + agent + ".json";
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
              "--summary-export", k6OutputFile,
              "/app/basic.js"
          )
          .withStartupCheckStrategy(
              new OneShotStartupCheckStrategy().withTimeout(Duration.ofMinutes(5))
          );
    }
  }
}
