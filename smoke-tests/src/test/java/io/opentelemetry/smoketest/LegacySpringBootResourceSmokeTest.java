/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@DisabledIf("io.opentelemetry.smoketest.TestContainerManager#useWindowsContainers")
class LegacySpringBootResourceSmokeTest {
  @Test
  void injectedResourcesInExecutableJar() throws IOException {
    String appPath = System.getProperty("io.opentelemetry.smoketest.springboot13.path");
    try (JarFile app = new JarFile(appPath)) {
      assertThat(app.getManifest().getMainAttributes().getValue("Main-Class"))
          .isEqualTo("org.springframework.boot.loader.JarLauncher");
      assertThat(app.getManifest().getMainAttributes().getValue("Spring-Boot-Version"))
          .isEqualTo("1.3.8.RELEASE");
      assertThat(app.getEntry("otel-smoke/injected-resource.txt")).isNull();
      assertThat(app.getEntry("resources/injected-resource.txt")).isNull();
    }
    try (GenericContainer<?> target =
        new GenericContainer<>(DockerImageName.parse("eclipse-temurin:8"))
            .withCopyFileToContainer(
                MountableFile.forHostPath(
                    System.getProperty("io.opentelemetry.smoketest.agent.shadowJar.path")),
                "/opentelemetry-javaagent.jar")
            .withCopyFileToContainer(
                MountableFile.forHostPath(
                    System.getProperty("io.opentelemetry.smoketest.extension.path")),
                "/extension.jar")
            .withCopyFileToContainer(MountableFile.forHostPath(appPath), "/app.jar")
            .withLogConsumer(
                new Slf4jLogConsumer(
                    LoggerFactory.getLogger(LegacySpringBootResourceSmokeTest.class)))
            .withStartupCheckStrategy(
                new OneShotStartupCheckStrategy().withTimeout(Duration.ofMinutes(2)))
            .withCommand(
                "java",
                "-javaagent:/opentelemetry-javaagent.jar",
                "-Dotel.javaagent.extensions=/extension.jar",
                "-Dotel.javaagent.testing.additional-library-ignores.enabled=true",
                "-Dotel.traces.exporter=none",
                "-Dotel.metrics.exporter=none",
                "-Dotel.logs.exporter=none",
                "-jar",
                "/app.jar")) {
      target.start();
      assertThat(target.getCurrentContainerInfo().getState().getExitCodeLong()).isZero();
      assertThat(target.getLogs())
          .contains(
              "LOADER org.springframework.boot.loader.LaunchedURLClassLoader",
              "RESOURCE getResource OK",
              "RESOURCE getResources OK",
              "RESOURCE getResourceAsStream OK")
          .doesNotContain("RESOURCE assertions failed");
    }
  }
}
