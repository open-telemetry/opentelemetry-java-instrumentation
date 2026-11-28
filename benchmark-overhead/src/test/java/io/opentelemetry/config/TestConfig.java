/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.config;

import io.opentelemetry.agents.Agent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Defines a test config. */
public class TestConfig {

  private static final int DEFAULT_MAX_REQUEST_RATE = 0; // none
  private static final int DEFAULT_CONCURRENT_CONNECTIONS = 5;
  private static final int DEFAULT_TOTAL_ITERATIONS = 5000;

  private final String name;
  private final String description;
  private final List<Agent> agents;
  private final int maxRequestRate;
  private final int concurrentConnections;
  private final int totalIterations;
  private final int warmupSeconds;
  private final Map<Agent, WebFluxMode> webFluxModes;

  public record WebFluxMode(boolean reactor, boolean automatic) {}

  public TestConfig(Builder builder) {
    this.name = builder.name;
    this.description = builder.description;
    this.agents = Collections.unmodifiableList(builder.agents);
    this.maxRequestRate = builder.maxRequestRate;
    this.concurrentConnections = builder.concurrentConnections;
    this.totalIterations = builder.totalIterations;
    this.warmupSeconds = builder.warmupSeconds;
    this.webFluxModes = Map.copyOf(builder.webFluxModes);
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public List<Agent> getAgents() {
    return Collections.unmodifiableList(agents);
  }

  public int getMaxRequestRate() {
    return maxRequestRate;
  }

  public int getConcurrentConnections() {
    return concurrentConnections;
  }

  public int getTotalIterations() {
    return totalIterations;
  }

  public int getWarmupSeconds() {
    return warmupSeconds;
  }

  public boolean isWebFlux() {
    return !webFluxModes.isEmpty();
  }

  public WebFluxMode webFluxMode(Agent agent) {
    return webFluxModes.get(agent);
  }

  public static Builder builder() {
    return new Builder();
  }

  static class Builder {
    private String name;
    private String description;
    private List<Agent> agents = new ArrayList<>();
    private int maxRequestRate = DEFAULT_MAX_REQUEST_RATE;
    private int concurrentConnections = DEFAULT_CONCURRENT_CONNECTIONS;
    private int totalIterations = DEFAULT_TOTAL_ITERATIONS;
    public int warmupSeconds = 0;
    private final Map<Agent, WebFluxMode> webFluxModes = new HashMap<>();

    Builder name(String name) {
      this.name = name;
      return this;
    }

    Builder description(String description) {
      this.description = description;
      return this;
    }

    Builder withAgents(Agent... agents) {
      this.agents.addAll(Arrays.asList(agents));
      return this;
    }

    Builder withWebFlux(Agent agent, boolean reactor, boolean automatic) {
      agents.add(agent);
      webFluxModes.put(agent, new WebFluxMode(reactor, automatic));
      return this;
    }

    Builder maxRequestRate(int maxRequestRate) {
      this.maxRequestRate = maxRequestRate;
      return this;
    }

    Builder concurrentConnections(int concurrentConnections) {
      this.concurrentConnections = concurrentConnections;
      return this;
    }

    Builder totalIterations(int totalIterations) {
      this.totalIterations = totalIterations;
      return this;
    }

    Builder warmupSeconds(int warmupSeconds) {
      this.warmupSeconds = warmupSeconds;
      return this;
    }

    TestConfig build() {
      return new TestConfig(this);
    }
  }
}
