/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.opentelemetry.config;

import io.opentelemetry.agents.Agent;
import java.util.Arrays;
import java.util.stream.Stream;

/** Defines all test configurations */
public enum Configs {
  RELEASE(
      TestConfig.builder()
          .name("release")
          .description("compares the latest stable release to no agent")
          .withAgents(Agent.NONE, Agent.LATEST_RELEASE)
          .warmupSeconds(30)
          .build()),
  SNAPSHOT(
      TestConfig.builder()
          .name("snapshot")
          .description("compares the latest snapshot to no agent")
          .withAgents(Agent.NONE, Agent.LATEST_SNAPSHOT)
          .warmupSeconds(30)
          .build()),
  SNAPSHOT_REGRESSION(
      TestConfig.builder()
          .name("snapshot-regression")
          .description("compares the latest snapshot to the latest stable release")
          .withAgents(Agent.LATEST_RELEASE, Agent.LATEST_SNAPSHOT)
          .warmupSeconds(30)
          .build());

  public final TestConfig config;

  public static Stream<TestConfig> all() {
    return Arrays.stream(Configs.values()).map(x -> x.config);
  }

  Configs(TestConfig config) {
    this.config = config;
  }
}
