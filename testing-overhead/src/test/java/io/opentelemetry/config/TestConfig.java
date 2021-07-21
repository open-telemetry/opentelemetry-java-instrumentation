/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.opentelemetry.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Defines a test config.
 */
public class TestConfig {

  private final static int DEFAULT_MAX_REQUEST_RATE = 0;  // none
  private final static int DEFAULT_CONCURRENT_CONNECTIONS = 5;
  private final static int DEFAULT_TOTAL_ITERATIONS = 500;

  private final String name;
  private final String description;
  private final List<String> agents;
  private final int maxRequestRate;
  private final int concurrentConnections;
  private final int totalIterations;

  public TestConfig(Builder builder) {
    this.name = builder.name;
    this.description = builder.description;
    this.agents = Collections.unmodifiableList(builder.agents);
    this.maxRequestRate = builder.maxRequestRate;
    this.concurrentConnections = builder.concurrentConnections;
    this.totalIterations = builder.totalIterations;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public List<String> getAgents() {
    return agents;
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

  static Builder builder() {
    return new Builder();
  }

  static class Builder {
    private String name;
    private String description;
    private List<String> agents = new ArrayList<>();
    private int maxRequestRate = DEFAULT_MAX_REQUEST_RATE;
    private int concurrentConnections = DEFAULT_CONCURRENT_CONNECTIONS;
    private int totalIterations = DEFAULT_TOTAL_ITERATIONS;

    Builder name(String name) {
      this.name = name;
      return this;
    }

    Builder description(String description) {
      this.description = description;
      return this;
    }

    Builder withAgents(String ...agents) {
      this.agents.addAll(Arrays.asList(agents));
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

    TestConfig build(){
      return new TestConfig(this);
    }
  }
}
