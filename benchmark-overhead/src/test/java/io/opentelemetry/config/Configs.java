/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.config;

import io.opentelemetry.agents.Agent;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Stream;

/** Defines all test configurations */
public enum Configs {
  RELEASE(
      petclinic()
          .name("release")
          .description("compares no agent, latest stable, and latest snapshot agents")
          .withAgents(Agent.NONE, Agent.LATEST_RELEASE, Agent.LATEST_SNAPSHOT)
          .build());

  public final TestConfig config;

  public static Stream<TestConfig> all() {
    return Arrays.stream(Configs.values()).map(x -> x.config);
  }

  public static TestConfig manualPetclinic(Path agentDir) {
    return petclinic()
        .name("petclinic")
        .description("compares no agent, latest release, and dispatched revision")
        .withAgents(Agent.NONE, latestRelease(agentDir), revision(agentDir, "revision"))
        .build();
  }

  public static Stream<TestConfig> manualWebFlux(Path agentDir) {
    return Stream.of(
        TestConfig.builder()
            .name("standard")
            .warmupSeconds(60)
            .withWebFlux(Agent.NONE, false, true)
            .withWebFlux(latestRelease(agentDir), true, true)
            .withWebFlux(revision(agentDir, "revision"), true, true)
            .build(),
        TestConfig.builder()
            .name("reactor")
            .warmupSeconds(60)
            .withWebFlux(revision(agentDir, "reactor-off-auto-off"), false, false)
            .withWebFlux(revision(agentDir, "reactor-on-auto-off"), true, false)
            .withWebFlux(revision(agentDir, "reactor-off-auto-on"), false, true)
            .withWebFlux(revision(agentDir, "reactor-on-auto-on"), true, true)
            .build());
  }

  private static Agent latestRelease(Path agentDir) {
    return new Agent(
        "latest", "latest release", agentDir.resolve("release-agent.jar").toUri().toString());
  }

  private static Agent revision(Path agentDir, String name) {
    return new Agent(
        name, "dispatched revision", agentDir.resolve("revision-agent.jar").toUri().toString());
  }

  private static TestConfig.Builder petclinic() {
    return TestConfig.builder().warmupSeconds(60);
  }

  Configs(TestConfig config) {
    this.config = config;
  }
}
