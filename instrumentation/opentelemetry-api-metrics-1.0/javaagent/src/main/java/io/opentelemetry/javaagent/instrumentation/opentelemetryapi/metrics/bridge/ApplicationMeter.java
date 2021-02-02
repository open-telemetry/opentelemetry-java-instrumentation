/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.metrics.bridge;

import application.io.opentelemetry.api.metrics.BatchRecorder;
import application.io.opentelemetry.api.metrics.DoubleCounterBuilder;
import application.io.opentelemetry.api.metrics.DoubleSumObserverBuilder;
import application.io.opentelemetry.api.metrics.DoubleUpDownCounterBuilder;
import application.io.opentelemetry.api.metrics.DoubleUpDownSumObserverBuilder;
import application.io.opentelemetry.api.metrics.DoubleValueObserverBuilder;
import application.io.opentelemetry.api.metrics.DoubleValueRecorderBuilder;
import application.io.opentelemetry.api.metrics.LongCounterBuilder;
import application.io.opentelemetry.api.metrics.LongSumObserverBuilder;
import application.io.opentelemetry.api.metrics.LongUpDownCounterBuilder;
import application.io.opentelemetry.api.metrics.LongUpDownSumObserverBuilder;
import application.io.opentelemetry.api.metrics.LongValueObserverBuilder;
import application.io.opentelemetry.api.metrics.LongValueRecorderBuilder;
import application.io.opentelemetry.api.metrics.Meter;

class ApplicationMeter implements Meter {

  private final io.opentelemetry.api.metrics.Meter agentMeter;

  ApplicationMeter(io.opentelemetry.api.metrics.Meter agentMeter) {
    this.agentMeter = agentMeter;
  }

  @Override
  public DoubleCounterBuilder doubleCounterBuilder(String name) {
    return new ApplicationDoubleCounter.Builder(agentMeter.doubleCounterBuilder(name));
  }

  @Override
  public LongCounterBuilder longCounterBuilder(String name) {
    return new ApplicationLongCounter.Builder(agentMeter.longCounterBuilder(name));
  }

  @Override
  public DoubleUpDownCounterBuilder doubleUpDownCounterBuilder(String name) {
    return new ApplicationDoubleUpDownCounter.Builder(agentMeter.doubleUpDownCounterBuilder(name));
  }

  @Override
  public LongUpDownCounterBuilder longUpDownCounterBuilder(String name) {
    return new ApplicationLongUpDownCounter.Builder(agentMeter.longUpDownCounterBuilder(name));
  }

  @Override
  public DoubleValueRecorderBuilder doubleValueRecorderBuilder(String name) {
    return new ApplicationDoubleValueRecorder.Builder(agentMeter.doubleValueRecorderBuilder(name));
  }

  @Override
  public LongValueRecorderBuilder longValueRecorderBuilder(String name) {
    return new ApplicationLongValueRecorder.Builder(agentMeter.longValueRecorderBuilder(name));
  }

  @Override
  public DoubleSumObserverBuilder doubleSumObserverBuilder(String name) {
    return new ApplicationDoubleSumObserver.Builder(agentMeter.doubleSumObserverBuilder(name));
  }

  @Override
  public LongSumObserverBuilder longSumObserverBuilder(String name) {
    return new ApplicationLongSumObserver.Builder(agentMeter.longSumObserverBuilder(name));
  }

  @Override
  public DoubleUpDownSumObserverBuilder doubleUpDownSumObserverBuilder(String name) {
    return new ApplicationDoubleUpDownSumObserver.Builder(
        agentMeter.doubleUpDownSumObserverBuilder(name));
  }

  @Override
  public LongUpDownSumObserverBuilder longUpDownSumObserverBuilder(String name) {
    return new ApplicationLongUpDownSumObserver.Builder(
        agentMeter.longUpDownSumObserverBuilder(name));
  }

  @Override
  public DoubleValueObserverBuilder doubleValueObserverBuilder(String name) {
    return new ApplicationDoubleValueObserver.Builder(agentMeter.doubleValueObserverBuilder(name));
  }

  @Override
  public LongValueObserverBuilder longValueObserverBuilder(String name) {
    return new ApplicationLongValueObserver.Builder(agentMeter.longValueObserverBuilder(name));
  }

  @Override
  public BatchRecorder newBatchRecorder(String... keyValuePairs) {
    return new ApplicationBatchRecorder(agentMeter.newBatchRecorder(keyValuePairs));
  }
}
