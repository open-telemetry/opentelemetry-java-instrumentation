/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest

import java.time.Duration
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testcontainers.containers.Container
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import org.testcontainers.utility.MountableFile
import spock.lang.Specification

// Hotspot versions before 8u40 crash in jit compiled lambdas when javaagent initializes
// java.lang.invoke.CallSite
// This test verifies that such jvm does not crash with opentelemetry agent
class CrashEarlyJdk8Test extends Specification {
  private static final Logger logger = LoggerFactory.getLogger(CrashEarlyJdk8Test)

  private static final String TARGET_AGENT_FILENAME = "opentelemetry-javaagent.jar"
  private static final String agentPath = System.getProperty("io.opentelemetry.smoketest.agent.shadowJar.path")

  def "test crash on early jdk8"() {
    setup:
    GenericContainer target =
      new GenericContainer<>(DockerImageName.parse("azul/zulu-openjdk:8u31"))
        .withStartupTimeout(Duration.ofMinutes(5))
        .withLogConsumer(new Slf4jLogConsumer(logger))
        .withCopyFileToContainer(
          MountableFile.forHostPath(agentPath), "/" + TARGET_AGENT_FILENAME)
        .withCopyFileToContainer(
          MountableFile.forClasspathResource("crashearlyjdk8/CrashEarlyJdk8.java"), "/CrashEarlyJdk8.java")
        .withCopyFileToContainer(
          MountableFile.forClasspathResource("crashearlyjdk8/start.sh", 777), "/start.sh")
        .withCopyFileToContainer(
          MountableFile.forClasspathResource("crashearlyjdk8/test.sh", 777), "/test.sh")
        .waitingFor(
          Wait.forLogMessage(".*started.*\\n", 1)
        )
        .withCommand("/bin/sh", "-c", "/start.sh")
    target.start()

    when:
    Container.ExecResult result = target.execInContainer("/bin/sh", "-c", "/test.sh")
    then:
    result.getExitCode() == 0

    cleanup:
    System.err.println(result.toString())
    target.stop()
  }
}
