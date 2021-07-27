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
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

public class PostgresContainer {

  private static final Logger logger = LoggerFactory.getLogger(PostgresContainer.class);
  private final Network network;

  public PostgresContainer(Network network) {
    this.network = network;
  }

  public GenericContainer<?> build() throws Exception {
    return new GenericContainer<>(
        DockerImageName.parse("postgres:9.6.22"))
        .withNetwork(network)
        .withNetworkAliases("postgres")
        .withLogConsumer(new Slf4jLogConsumer(logger))
        .withEnv("POSTGRES_PASSWORD", "petclinic")
        .withEnv("POSTGRES_DB", "petclinic")
        .withCopyFileToContainer(
            MountableFile.forClasspathResource("initDB.sql"), "/docker-entrypoint-initdb.d/initDB.sql")
        .withCopyFileToContainer(
            MountableFile.forClasspathResource("populateDB.sql"), "/docker-entrypoint-initdb.d/populateDB.sql")
        .withReuse(false)
        .withExposedPorts(5432);
  }

}
