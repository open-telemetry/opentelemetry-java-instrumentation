/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.opentelemetry.results;

import io.opentelemetry.agents.Agent;
import io.opentelemetry.config.TestConfig;

public class AppPerfResults {

  final Agent agent;
  final TestConfig config;
  final double iterationAvg;
  final double iterationP95;
  final double requestAvg;
  final double requestP95;
  final long totalGCTime;
  final long totalAllocated;
  final MinMax heapUsed;
  final float maxThreadContextSwitchRate;
  final long startupDurationMs;
  final long peakThreadCount;
  final long averageNetworkRead;
  final long averageNetworkWrite;
  final float averageJvmUserCpu;
  final float maxJvmUserCpu;
  final float averageMachineCpuTotal;
  final long runDurationMs;
  final long totalGcPauseNanos;

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
    this.peakThreadCount = builder.peakThreadCount;
    this.averageNetworkRead = builder.averageNetworkRead;
    this.averageNetworkWrite = builder.averageNetworkWrite;
    this.averageJvmUserCpu = builder.averageJvmUserCpu;
    this.maxJvmUserCpu = builder.maxJvmUserCpu;
    this.averageMachineCpuTotal = builder.averageMachineCpuTotal;
    this.runDurationMs = builder.runDurationMs;
    this.totalGcPauseNanos = builder.totalGcPauseNanos;
  }

  double getTotalAllocatedMB() {
    return bytesToMegs(this.totalAllocated);
  }

  double getMinHeapUsedMB(){
    return bytesToMegs(this.heapUsed.min);
  }

  double getMaxHeapUsedMB(){
    return bytesToMegs(this.heapUsed.max);
  }

  private double bytesToMegs(long x) {
    return x / (1024.0 * 1024.0);
  }

  String getAgentName() {
    return agent.getName();
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
    private long peakThreadCount;
    public long averageNetworkRead;
    public long averageNetworkWrite;
    public float averageJvmUserCpu;
    public float maxJvmUserCpu;
    public float averageMachineCpuTotal;
    public long runDurationMs;
    public long totalGcPauseNanos;

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

    Builder startupDurationMs(long startupDurationMs) {
      this.startupDurationMs = startupDurationMs;
      return this;
    }

    Builder peakThreadCount(long peakThreadCount) {
      this.peakThreadCount = peakThreadCount;
      return this;
    }

    Builder averageNetworkRead(long averageNetworkRead) {
      this.averageNetworkRead = averageNetworkRead;
      return this;
    }

    Builder averageNetworkWrite(long averageNetworkWrite){
      this.averageNetworkWrite = averageNetworkWrite;
      return this;
    }

    Builder averageJvmUserCpu(float averageJvmUserCpu){
      this.averageJvmUserCpu = averageJvmUserCpu;
      return this;
    }

    Builder maxJvmUserCpu(float maxJvmUserCpu){
      this.maxJvmUserCpu = maxJvmUserCpu;
      return this;
    }

    Builder averageMachineCpuTotal(float averageMachineCpuTotal){
      this.averageMachineCpuTotal = averageMachineCpuTotal;
      return this;
    }

    Builder runDurationMs(long runDurationMs){
      this.runDurationMs = runDurationMs;
      return this;
    }

    Builder totalGcPauseNanos(long totalGcPauseNanos){
      this.totalGcPauseNanos = totalGcPauseNanos;
      return this;
    }
  }

  public static class MinMax {
    public final long min;
    public final long max;

    public MinMax(){
      this(Long.MAX_VALUE, Long.MIN_VALUE);
    }

    public MinMax(long min, long max){
      this.min = min;
      this.max = max;
    }

    public MinMax withMin(long min){
      return new MinMax(min, max);
    }

    public MinMax withMax(long max){
      return new MinMax(min, max);
    }
  }
}
