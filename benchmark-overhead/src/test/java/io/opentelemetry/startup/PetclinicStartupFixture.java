/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.startup;

import com.jayway.jsonpath.JsonPath;
import io.opentelemetry.containers.PostgresContainer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

final class PetclinicStartupFixture implements AutoCloseable {
  static final String IMAGE =
      "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/"
          + "petclinic-rest-base@sha256:2f1c1ef26b7e847a5b175326ed998b1fa7a50177cb0e0cacb555d6f15cb96d8e";
  static final String DATABASE_IMAGE =
      "postgres@sha256:3273a713362e7bc2c2c4ea9a653b24febf2653c5d5c95003d81f6d2fdd415f18";
  static final String READINESS_PATH = "/petclinic/api/owners/1";

  private final Network network;
  private final PostgreSQLContainer<?> database;
  private final Path annotations;

  private PetclinicStartupFixture(
      Network network, PostgreSQLContainer<?> database, Path annotations) {
    this.network = network;
    this.database = database;
    this.annotations = annotations;
  }

  static PetclinicStartupFixture start(Path annotations) throws Exception {
    if (!Files.isRegularFile(annotations)) {
      throw new IllegalArgumentException("Annotation JAR does not exist: " + annotations);
    }
    try (JarFile jar = new JarFile(annotations.toFile())) {
      if (jar.getJarEntry("io/opentelemetry/instrumentation/annotations/WithSpan.class") == null) {
        throw new IllegalArgumentException("Invalid annotation JAR: " + annotations);
      }
    }
    Network network = Network.newNetwork();
    PostgreSQLContainer<?> database = null;
    try {
      database = new PostgresContainer(network, DATABASE_IMAGE).build();
      database.start();
      var seed =
          database.execInContainer(
              "psql",
              "-U",
              PostgresContainer.USERNAME,
              "-d",
              PostgresContainer.DATABASE_NAME,
              "-tAc",
              "update owners set first_name = 'StartupBenchmark' where id = 1 and first_name = 'George' and last_name = 'Franklin' returning first_name || ' ' || last_name");
      if (seed.getExitCode() != 0 || !seed.getStdout().contains("StartupBenchmark Franklin")) {
        throw new IllegalStateException("Postgres seed verification failed: " + seed);
      }
      return new PetclinicStartupFixture(network, database, annotations);
    } catch (Throwable t) {
      if (database != null) {
        database.close();
      }
      network.close();
      throw t;
    }
  }

  GenericContainer<?> configure(GenericContainer<?> application) {
    return application
        .withNetwork(network)
        .withCopyFileToContainer(
            MountableFile.forHostPath(annotations), "/app/annotations/annotations.jar")
        .withEnv("spring_profiles_active", "postgresql,spring-data-jpa")
        .withEnv(
            "spring_datasource_url",
            "jdbc:postgresql://postgres:5432/" + PostgresContainer.DATABASE_NAME)
        .withEnv("spring_datasource_username", PostgresContainer.USERNAME)
        .withEnv("spring_datasource_password", PostgresContainer.PASSWORD)
        .withEnv("spring_jpa_hibernate_ddl-auto", "none");
  }

  static void validateResponse(int status, String body) {
    try {
      Number id = JsonPath.read(body, "$.id");
      String firstName = JsonPath.read(body, "$.firstName");
      String lastName = JsonPath.read(body, "$.lastName");
      if (status != 200
          || id.intValue() != 1
          || !"StartupBenchmark".equals(firstName)
          || !"Franklin".equals(lastName)) {
        throw new IllegalArgumentException("Unexpected Petclinic seeded owner");
      }
    } catch (RuntimeException e) {
      throw new IllegalArgumentException(
          "Petclinic readiness must return PostgreSQL seeded owner 1 (StartupBenchmark Franklin), HTTP "
              + status
              + ": "
              + body,
          e);
    }
  }

  @Override
  public void close() {
    try {
      database.close();
    } finally {
      network.close();
    }
  }
}
