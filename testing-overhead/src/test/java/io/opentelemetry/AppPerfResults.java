/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.opentelemetry;

import io.opentelemetry.agents.Agent;
import io.opentelemetry.config.TestConfig;

class AppPerfResults {

  private final Agent agent;
  private final TestConfig config;
  private final double iterationAvg;
  private final double iterationP95;
  private final double requestAvg;
  private final double requestP95;
  private final long totalGCTime;
  private final long totalAllocated;
  private final MinMax heapUsed;
  private final float maxThreadContextSwitchRate;
  private final long startupDurationMs;

  private AppPerfResults(Builder builder) {
    this.agent = builder.agent;
    this.config = builder.config;
    this.iterationAvg = builder.iterationAvg;
    this.iterationP95 = builder.iterationP95;
    this.requestAvg = builder.requestAvg;
    this.requestP95 = builder.requestP95;
    this.totalGCTime = builder.totalGCTime;
    this.totalAllocated = builder.totalAllocated;
    this.heapUsed = builder.heapUsed;
    this.maxThreadContextSwitchRate = builder.maxThreadContextSwitchRate;
    this.startupDurationMs = builder.startupDurationMs;
  }

   Agent getAgent() {
    return agent;
  }

   TestConfig getConfig() {
    return config;
  }

   double getIterationAvg() {
    return iterationAvg;
  }

   double getIterationP95() {
    return iterationP95;
  }

   double getRequestAvg() {
    return requestAvg;
  }

   double getRequestP95() {
    return requestP95;
  }

   long getTotalGCTime() {
    return totalGCTime;
  }

   long getTotalAllocated() {
    return totalAllocated;
  }

  double getTotalAllocatedMB() {
    return totalAllocated / (1024.0*1024.0);
  }

   MinMax getHeapUsed() {
    return heapUsed;
  }

   float getMaxThreadContextSwitchRate() {
    return maxThreadContextSwitchRate;
  }

   long getStartupDurationMs() {
    return startupDurationMs;
  }

  static Builder builder() {
    return new Builder();
  }

  static class Builder {
    private long startupDurationMs;
    private Agent agent;
    private TestConfig config;
    private double iterationAvg;
    private double iterationP95;
    private double requestAvg;
    private double requestP95;
    private long totalGCTime;
    private long totalAllocated;
    private MinMax heapUsed;
    private float maxThreadContextSwitchRate;

    AppPerfResults build() {
      return new AppPerfResults(this);
    }

    Builder agent(Agent agent) {
      this.agent = agent;
      return this;
    }

    Builder config(TestConfig config) {
      this.config = config;
      return this;
    }

    Builder iterationAvg(double iterationAvg) {
      this.iterationAvg = iterationAvg;
      return this;
    }

    Builder iterationP95(double iterationP95) {
      this.iterationP95 = iterationP95;
      return this;
    }

    Builder requestAvg(double requestAvg) {
      this.requestAvg = requestAvg;
      return this;
    }

    Builder requestP95(double requestP95) {
      this.requestP95 = requestP95;
      return this;
    }

    Builder totalGCTime(long totalGCTime) {
      this.totalGCTime = totalGCTime;
      return this;
    }

    Builder totalAllocated(long totalAllocated) {
      this.totalAllocated = totalAllocated;
      return this;
    }

    Builder heapUsed(MinMax heapUsed) {
      this.heapUsed = heapUsed;
      return this;
    }

    Builder maxThreadContextSwitchRate(float maxThreadContextSwitchRate) {
      this.maxThreadContextSwitchRate = maxThreadContextSwitchRate;
      return this;
    }

    Builder startupDurationMs(long startupDurationMs){
      this.startupDurationMs = startupDurationMs;
      return this;
    }
  }

  static class MinMax {
    long min = Long.MAX_VALUE;
    long max = Long.MIN_VALUE;
  }
}
