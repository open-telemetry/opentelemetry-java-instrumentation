/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

public class ExtensionsSmokeTest {

  private static final Logger logger = LoggerFactory.getLogger(ExtensionsSmokeTest.class);

  private static final String TARGET_AGENT_FILENAME = "/opentelemetry-javaagent.jar";
  private static final String agentPath =
      System.getProperty("io.opentelemetry.smoketest.agent.shadowJar.path");

  private static final String TARGET_EXTENSION_FILENAME = "/opentelemetry-extension.jar";
  private static final String extensionInlinePath =
      System.getProperty("io.opentelemetry.smoketest.extension.inline.path");

  private static final String TARGET_APP_FILENAME = "/app.jar";
  private static final String appPath =
      System.getProperty("io.opentelemetry.smoketest.extension.testapp.path");

  private static final String IMAGE = "eclipse-temurin:21";

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void inlinedExtension(boolean indy) throws InterruptedException {

    List<String> cmd = new ArrayList<>();
    cmd.add("java");
    cmd.add("-javaagent:" + TARGET_AGENT_FILENAME);

    Map<String, String> config = new HashMap<>();
    // disable export as we only instrument
    config.put("otel.logs.exporter", "none");
    config.put("otel.metrics.exporter", "none");
    config.put("otel.traces.exporter", "none");
    // add extension
    config.put("otel.javaagent.extensions", TARGET_EXTENSION_FILENAME);
    // toggle indy on/off
    config.put("otel.javaagent.experimental.indy", Boolean.toString(indy));
    // toggle debug if needed
    config.put("otel.javaagent.debug", "false");
    config.forEach((k, v) -> cmd.add(String.format("-D%s=%s", k, v)));

    cmd.add("-jar");
    cmd.add(TARGET_APP_FILENAME);

    GenericContainer<?> target =
        new GenericContainer<>(DockerImageName.parse(IMAGE))
            .withStartupTimeout(Duration.ofMinutes(1))
            .withLogConsumer(new Slf4jLogConsumer(logger))
            .withCopyFileToContainer(MountableFile.forHostPath(agentPath), TARGET_AGENT_FILENAME)
            .withCopyFileToContainer(
                MountableFile.forHostPath(extensionInlinePath), TARGET_EXTENSION_FILENAME)
            .withCopyFileToContainer(MountableFile.forHostPath(appPath), TARGET_APP_FILENAME)
            .withCommand(String.join(" ", cmd));

    logger.info("starting JVM with command: " + String.join(" ", cmd));
    target.start();
    while (target.isRunning()) {
      Thread.sleep(100);
    }

    List<String> appOutput =
        Arrays.asList(target.getLogs(OutputFrame.OutputType.STDOUT).split("\n"));
    assertThat(appOutput)
        .describedAs("return value instrumentation")
        .contains("return value has been modified")
        .describedAs("argument value instrumentation")
        .contains("argument has been modified");
    // TODO add assertion for virtual fields
  }
}
