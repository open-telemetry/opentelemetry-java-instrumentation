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
        .withAgents(
            Agent.NONE,
            new Agent(
                "latest",
                "latest release",
                agentDir.resolve("release-agent.jar").toUri().toString()),
            new Agent(
                "revision",
                "dispatched revision",
                agentDir.resolve("revision-agent.jar").toUri().toString()))
        .build();
  }

  private static TestConfig.Builder petclinic() {
    return TestConfig.builder().warmupSeconds(60);
  }

  Configs(TestConfig config) {
    this.config = config;
  }
}
