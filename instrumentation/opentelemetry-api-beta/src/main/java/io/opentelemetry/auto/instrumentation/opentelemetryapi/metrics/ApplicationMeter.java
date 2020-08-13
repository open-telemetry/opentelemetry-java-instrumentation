/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.auto.instrumentation.opentelemetryapi.metrics;

import application.io.opentelemetry.metrics.BatchRecorder;
import application.io.opentelemetry.metrics.DoubleCounter;
import application.io.opentelemetry.metrics.DoubleSumObserver;
import application.io.opentelemetry.metrics.DoubleUpDownCounter;
import application.io.opentelemetry.metrics.DoubleUpDownSumObserver;
import application.io.opentelemetry.metrics.DoubleValueObserver;
import application.io.opentelemetry.metrics.DoubleValueRecorder;
import application.io.opentelemetry.metrics.LongCounter;
import application.io.opentelemetry.metrics.LongSumObserver;
import application.io.opentelemetry.metrics.LongUpDownCounter;
import application.io.opentelemetry.metrics.LongUpDownSumObserver;
import application.io.opentelemetry.metrics.LongValueObserver;
import application.io.opentelemetry.metrics.LongValueRecorder;
import application.io.opentelemetry.metrics.Meter;

class ApplicationMeter implements Meter {

  private final io.opentelemetry.metrics.Meter agentMeter;

  ApplicationMeter(final io.opentelemetry.metrics.Meter agentMeter) {
    this.agentMeter = agentMeter;
  }

  @Override
  public DoubleCounter.Builder doubleCounterBuilder(final String name) {
    return new ApplicationDoubleCounter.Builder(agentMeter.doubleCounterBuilder(name));
  }

  @Override
  public LongCounter.Builder longCounterBuilder(final String name) {
    return new ApplicationLongCounter.Builder(agentMeter.longCounterBuilder(name));
  }

  @Override
  public DoubleUpDownCounter.Builder doubleUpDownCounterBuilder(final String name) {
    return new ApplicationDoubleUpDownCounter.Builder(agentMeter.doubleUpDownCounterBuilder(name));
  }

  @Override
  public LongUpDownCounter.Builder longUpDownCounterBuilder(final String name) {
    return new ApplicationLongUpDownCounter.Builder(agentMeter.longUpDownCounterBuilder(name));
  }

  @Override
  public DoubleValueRecorder.Builder doubleValueRecorderBuilder(final String name) {
    return new ApplicationDoubleValueRecorder.Builder(agentMeter.doubleValueRecorderBuilder(name));
  }

  @Override
  public LongValueRecorder.Builder longValueRecorderBuilder(final String name) {
    return new ApplicationLongValueRecorder.Builder(agentMeter.longValueRecorderBuilder(name));
  }

  @Override
  public DoubleSumObserver.Builder doubleSumObserverBuilder(final String name) {
    return new ApplicationDoubleSumObserver.Builder(agentMeter.doubleSumObserverBuilder(name));
  }

  @Override
  public LongSumObserver.Builder longSumObserverBuilder(final String name) {
    return new ApplicationLongSumObserver.Builder(agentMeter.longSumObserverBuilder(name));
  }

  @Override
  public DoubleUpDownSumObserver.Builder doubleUpDownSumObserverBuilder(final String name) {
    return new ApplicationDoubleUpDownSumObserver.Builder(
        agentMeter.doubleUpDownSumObserverBuilder(name));
  }

  @Override
  public LongUpDownSumObserver.Builder longUpDownSumObserverBuilder(final String name) {
    return new ApplicationLongUpDownSumObserver.Builder(
        agentMeter.longUpDownSumObserverBuilder(name));
  }

  @Override
  public DoubleValueObserver.Builder doubleValueObserverBuilder(final String name) {
    return new ApplicationDoubleValueObserver.Builder(agentMeter.doubleValueObserverBuilder(name));
  }

  @Override
  public LongValueObserver.Builder longValueObserverBuilder(final String name) {
    return new ApplicationLongValueObserver.Builder(agentMeter.longValueObserverBuilder(name));
  }

  @Override
  public BatchRecorder newBatchRecorder(final String... keyValuePairs) {
    return new ApplicationBatchRecorder(agentMeter.newBatchRecorder(keyValuePairs));
  }
}
