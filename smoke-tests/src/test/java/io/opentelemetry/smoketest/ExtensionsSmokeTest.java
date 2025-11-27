/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import java.time.Duration;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

public class ExtensionsSmokeTest {

  private static final Logger logger = LoggerFactory.getLogger(ExtensionsSmokeTest.class);

  private static final String TARGET_AGENT_FILENAME = "/opentelemetry-javaagent.jar";
  private static final String agentPath =
      System.getProperty("io.opentelemetry.smoketest.agent.shadowJar.path");

  private static final String TARGET_EXTENSION_FILENAME = "/opentelemetry-extension.jar";
  private static final String extensionInlinePath =
      System.getProperty("io.opentelemetry.smoketest.extension.inline.path");

  private static final String IMAGE_VERSION = "jdk17-20251127.135061";

  @Test
  void inlinedExtension() throws InterruptedException {
    GenericContainer<?> target = new GenericContainer<>(
        DockerImageName.parse(
            "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-extensions:"
                + IMAGE_VERSION))
        .withStartupTimeout(Duration.ofMinutes(1))
        .withLogConsumer(new Slf4jLogConsumer(logger))
        // disable export as we only instrument
        .withEnv("OTEL_TRACES_EXPORTER", "none")
        .withEnv("OTEL_METRICS_EXPORTER", "none")
        .withEnv("OTEL_LOGS_EXPORTER", "none")
        //
        .withEnv("JAVA_TOOL_OPTIONS", "-javaagent:" + TARGET_AGENT_FILENAME)
        .withEnv("OTEL_JAVAAGENT_EXTENSIONS", TARGET_EXTENSION_FILENAME)
        .withCopyFileToContainer(
            MountableFile.forHostPath(agentPath), TARGET_AGENT_FILENAME)
        .withCopyFileToContainer(
            MountableFile.forHostPath(extensionInlinePath), TARGET_EXTENSION_FILENAME);
    target.start();
    while (target.isRunning()) {
      Thread.sleep(1000);
    }
    assertThat(target.getLogs().split("\n"))
        .contains("return value not modified", "argument not modified");
  }


}
