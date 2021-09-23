/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.opentelemetry.containers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

public class CollectorContainer {

  static final int COLLECTOR_PORT = 4317;
  static final int COLLECTOR_HEALTH_CHECK_PORT = 13133;

  private static final Logger logger = LoggerFactory.getLogger(CollectorContainer.class);

  public static GenericContainer<?> build(Network network) {

    return new GenericContainer<>(
            DockerImageName.parse("otel/opentelemetry-collector-contrib:latest"))
        .withNetwork(network)
        .withNetworkAliases("collector")
        .withLogConsumer(new Slf4jLogConsumer(logger))
        .withExposedPorts(COLLECTOR_PORT, COLLECTOR_HEALTH_CHECK_PORT)
        .waitingFor(Wait.forHttp("/health").forPort(COLLECTOR_HEALTH_CHECK_PORT))
        .withCopyFileToContainer(
            MountableFile.forClasspathResource("collector.yaml"), "/etc/otel.yaml")
        .withCommand("--config /etc/otel.yaml");
  }
}
