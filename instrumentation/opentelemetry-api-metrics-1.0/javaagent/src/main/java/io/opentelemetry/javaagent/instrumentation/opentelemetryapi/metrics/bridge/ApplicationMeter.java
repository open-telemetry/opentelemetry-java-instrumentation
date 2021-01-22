/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.metrics.bridge;

import application.io.opentelemetry.api.metrics.BatchRecorder;
import application.io.opentelemetry.api.metrics.DoubleCounter;
import application.io.opentelemetry.api.metrics.DoubleSumObserver;
import application.io.opentelemetry.api.metrics.DoubleUpDownCounter;
import application.io.opentelemetry.api.metrics.DoubleUpDownSumObserver;
import application.io.opentelemetry.api.metrics.DoubleValueObserver;
import application.io.opentelemetry.api.metrics.DoubleValueRecorder;
import application.io.opentelemetry.api.metrics.LongCounter;
import application.io.opentelemetry.api.metrics.LongSumObserver;
import application.io.opentelemetry.api.metrics.LongUpDownCounter;
import application.io.opentelemetry.api.metrics.LongUpDownSumObserver;
import application.io.opentelemetry.api.metrics.LongValueObserver;
import application.io.opentelemetry.api.metrics.LongValueRecorder;
import application.io.opentelemetry.api.metrics.Meter;

class ApplicationMeter implements Meter {

  private final io.opentelemetry.api.metrics.Meter agentMeter;

  ApplicationMeter(io.opentelemetry.api.metrics.Meter agentMeter) {
    this.agentMeter = agentMeter;
  }

  @Override
  public DoubleCounter.Builder doubleCounterBuilder(String name) {
    return new ApplicationDoubleCounter.Builder(agentMeter.doubleCounterBuilder(name));
  }

  @Override
  public LongCounter.Builder longCounterBuilder(String name) {
    return new ApplicationLongCounter.Builder(agentMeter.longCounterBuilder(name));
  }

  @Override
  public DoubleUpDownCounter.Builder doubleUpDownCounterBuilder(String name) {
    return new ApplicationDoubleUpDownCounter.Builder(agentMeter.doubleUpDownCounterBuilder(name));
  }

  @Override
  public LongUpDownCounter.Builder longUpDownCounterBuilder(String name) {
    return new ApplicationLongUpDownCounter.Builder(agentMeter.longUpDownCounterBuilder(name));
  }

  @Override
  public DoubleValueRecorder.Builder doubleValueRecorderBuilder(String name) {
    return new ApplicationDoubleValueRecorder.Builder(agentMeter.doubleValueRecorderBuilder(name));
  }

  @Override
  public LongValueRecorder.Builder longValueRecorderBuilder(String name) {
    return new ApplicationLongValueRecorder.Builder(agentMeter.longValueRecorderBuilder(name));
  }

  @Override
  public DoubleSumObserver.Builder doubleSumObserverBuilder(String name) {
    return new ApplicationDoubleSumObserver.Builder(agentMeter.doubleSumObserverBuilder(name));
  }

  @Override
  public LongSumObserver.Builder longSumObserverBuilder(String name) {
    return new ApplicationLongSumObserver.Builder(agentMeter.longSumObserverBuilder(name));
  }

  @Override
  public DoubleUpDownSumObserver.Builder doubleUpDownSumObserverBuilder(String name) {
    return new ApplicationDoubleUpDownSumObserver.Builder(
        agentMeter.doubleUpDownSumObserverBuilder(name));
  }

  @Override
  public LongUpDownSumObserver.Builder longUpDownSumObserverBuilder(String name) {
    return new ApplicationLongUpDownSumObserver.Builder(
        agentMeter.longUpDownSumObserverBuilder(name));
  }

  @Override
  public DoubleValueObserver.Builder doubleValueObserverBuilder(String name) {
    return new ApplicationDoubleValueObserver.Builder(agentMeter.doubleValueObserverBuilder(name));
  }

  @Override
  public LongValueObserver.Builder longValueObserverBuilder(String name) {
    return new ApplicationLongValueObserver.Builder(agentMeter.longValueObserverBuilder(name));
  }

  @Override
  public BatchRecorder newBatchRecorder(String... keyValuePairs) {
    return new ApplicationBatchRecorder(agentMeter.newBatchRecorder(keyValuePairs));
  }
}
