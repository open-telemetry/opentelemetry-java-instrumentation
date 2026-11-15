/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@DisabledIf("io.opentelemetry.smoketest.TestContainerManager#useWindowsContainers")
class LegacySpringBootLogCorrelationSmokeTest {
  private static final Pattern EXPECTED_IDS =
      Pattern.compile("EXPECTED_IDS ([0-9a-f]{32}) ([0-9a-f]{16})");

  @Test
  void logCorrelationInExecutableJar() {
    String appPath = System.getProperty("io.opentelemetry.smoketest.springboot13.path");
    try (GenericContainer<?> target =
        new GenericContainer<>(DockerImageName.parse("eclipse-temurin:8"))
            .withCopyFileToContainer(
                MountableFile.forHostPath(
                    System.getProperty("io.opentelemetry.smoketest.agent.shadowJar.path")),
                "/opentelemetry-javaagent.jar")
            .withCopyFileToContainer(MountableFile.forHostPath(appPath), "/app.jar")
            .withLogConsumer(
                new Slf4jLogConsumer(
                    LoggerFactory.getLogger(LegacySpringBootLogCorrelationSmokeTest.class)))
            .withStartupCheckStrategy(
                new OneShotStartupCheckStrategy().withTimeout(Duration.ofMinutes(2)))
            .withCommand(
                "java",
                "-javaagent:/opentelemetry-javaagent.jar",
                "-Dotel.traces.exporter=logging",
                "-Dotel.metrics.exporter=none",
                "-Dotel.logs.exporter=none",
                "-jar",
                "/app.jar")) {
      target.start();
      String logs = target.getLogs();
      Matcher expectedIds = EXPECTED_IDS.matcher(logs);
      assertThat(expectedIds.find()).isTrue();
      assertThat(logs)
          .contains(
              "CORRELATED "
                  + expectedIds.group(1)
                  + " "
                  + expectedIds.group(2)
                  + " correlated log message");
    }
  }
}
