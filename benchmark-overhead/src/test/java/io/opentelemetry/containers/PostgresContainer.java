/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.containers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.utility.MountableFile;

public class PostgresContainer {

  private static final Logger logger = LoggerFactory.getLogger(PostgresContainer.class);
  public static final String PASSWORD = "petclinic";
  public static final String USERNAME = "petclinic";
  public static final String DATABASE_NAME = "petclinic";
  public static final String IMAGE =
      "postgres@sha256:eb63372c1e05c755f55fa30cbd07aecc443676fc6c0fa90b9002d01537841e1a";

  private final Network network;
  private final String image;

  public PostgresContainer(Network network) {
    this(network, IMAGE);
  }

  public PostgresContainer(Network network, String image) {
    this.network = network;
    this.image = image;
  }

  public PostgreSQLContainer<?> build() throws Exception {
    return new PostgreSQLContainer<>(image)
        .withNetwork(network)
        .withNetworkAliases("postgres")
        .withLogConsumer(new Slf4jLogConsumer(logger))
        .withUsername(USERNAME)
        .withPassword(PASSWORD)
        .withDatabaseName(DATABASE_NAME)
        .withCopyFileToContainer(
            MountableFile.forClasspathResource("initDB.sql"),
            "/docker-entrypoint-initdb.d/initDB.sql")
        .withCopyFileToContainer(
            MountableFile.forClasspathResource("populateDB.sql"),
            "/docker-entrypoint-initdb.d/populateDB.sql")
        .withReuse(false);
  }
}
