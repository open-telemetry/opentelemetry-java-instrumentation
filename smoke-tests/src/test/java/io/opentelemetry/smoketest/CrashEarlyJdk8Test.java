/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

// Hotspot versions before 8u40 crash in jit compiled lambdas when javaagent initializes
// java.lang.invoke.CallSite
// This test verifies that such jvm does not crash with opentelemetry agent
@DisabledIf("io.opentelemetry.smoketest.TestContainerManager#useWindowsContainers")
class CrashEarlyJdk8Test {
  private static final Logger logger = LoggerFactory.getLogger(CrashEarlyJdk8Test.class);

  private static final String TARGET_AGENT_FILENAME = "opentelemetry-javaagent.jar";
  private static final String agentPath =
      System.getProperty("io.opentelemetry.smoketest.agent.shadowJar.path");

  private GenericContainer<?> target;
  private Container.ExecResult result;

  @BeforeEach
  void setUp() {
    target =
        new GenericContainer<>(
                DockerImageName.parse(
                    "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-zulu-openjdk-8u31:"
                        + ImageVersions.ZULU_OPENJDK_8U31_VERSION))
            .withStartupTimeout(Duration.ofMinutes(5))
            .withLogConsumer(new Slf4jLogConsumer(logger))
            .withCopyFileToContainer(
                MountableFile.forHostPath(agentPath), "/" + TARGET_AGENT_FILENAME)
            .withCopyFileToContainer(
                MountableFile.forClasspathResource("crashearlyjdk8/CrashEarlyJdk8.java"),
                "/CrashEarlyJdk8.java")
            .withCopyFileToContainer(
                MountableFile.forClasspathResource("crashearlyjdk8/start.sh", 777), "/start.sh")
            .withCopyFileToContainer(
                MountableFile.forClasspathResource("crashearlyjdk8/test.sh", 777), "/test.sh")
            .waitingFor(Wait.forLogMessage(".*started.*\\n", 1))
            .withCommand("/bin/sh", "-c", "/start.sh");
    target.start();
  }

  @Test
  void testCrashOnEarlyJdk8() throws Exception {
    result = target.execInContainer("/bin/sh", "-c", "/test.sh");
    assertThat(result.getExitCode()).isZero();
  }

  @AfterEach
  @SuppressWarnings("SystemOut")
  void tearDown() {
    if (result != null) {
      System.err.println(result);
    }
    if (target != null) {
      target.stop();
    }
  }
}
