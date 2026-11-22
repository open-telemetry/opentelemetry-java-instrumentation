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

  private final Network network;
  private final String image;

  public PostgresContainer(Network network) {
    this(network, "postgres:18@sha256:ec003ee60c23d320d1b81868f255a6fc724ac06d8f1ced4468532f7d8593f357");
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
