/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.startup;

import com.jayway.jsonpath.JsonPath;
import io.opentelemetry.containers.PetClinicRestContainer;
import io.opentelemetry.containers.PostgresContainer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

final class PetclinicStartupFixture implements AutoCloseable {
  static final String IMAGE = PetClinicRestContainer.IMAGE;
  static final String DATABASE_IMAGE = PostgresContainer.IMAGE;
  static final String READINESS_PATH = "/petclinic/api/owners/1";

  private final Network network;
  private final PostgreSQLContainer<?> database;
  private final Path annotations;
  private final Path helper;

  private PetclinicStartupFixture(
      Network network, PostgreSQLContainer<?> database, Path annotations, Path helper) {
    this.network = network;
    this.database = database;
    this.annotations = annotations;
    this.helper = helper;
  }

  static PetclinicStartupFixture start(Path annotations, Path helper) throws Exception {
    if (!Files.isRegularFile(annotations)) {
      throw new IllegalArgumentException("Annotation JAR does not exist: " + annotations);
    }
    try (JarFile jar = new JarFile(annotations.toFile())) {
      if (jar.getJarEntry("io/opentelemetry/instrumentation/annotations/WithSpan.class") == null) {
        throw new IllegalArgumentException("Invalid annotation JAR: " + annotations);
      }
    }
    if (!Files.isRegularFile(helper)) {
      throw new IllegalArgumentException("Petclinic startup helper JAR does not exist: " + helper);
    }
    try (JarFile jar = new JarFile(helper.toFile())) {
      var advice =
          jar.getJarEntry("io/opentelemetry/startup/PetclinicStartupUptimeResponseAdvice.class");
      var registration =
          jar.getJarEntry(
              "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports");
      if (advice == null
          || registration == null
          || !new String(jar.getInputStream(registration).readAllBytes(), StandardCharsets.UTF_8)
              .lines()
              .anyMatch("io.opentelemetry.startup.PetclinicStartupUptimeResponseAdvice"::equals)) {
        throw new IllegalArgumentException("Invalid Petclinic startup helper JAR: " + helper);
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
      return new PetclinicStartupFixture(network, database, annotations, helper);
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
        .withCopyFileToContainer(
            MountableFile.forHostPath(helper), "/app/startup-helper/petclinic-startup-helper.jar")
        .withEnv("spring_profiles_active", "postgres,spring-data-jpa")
        .withEnv("spring_sql_init_mode", "never")
        .withEnv(
            "spring_datasource_url",
            "jdbc:postgresql://postgres:5432/" + PostgresContainer.DATABASE_NAME)
        .withEnv("spring_datasource_username", PostgresContainer.USERNAME)
        .withEnv("spring_datasource_password", PostgresContainer.PASSWORD)
        .withEnv("spring_jpa_hibernate_ddl-auto", "none");
  }

  Path helper() {
    return helper;
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
