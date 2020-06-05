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

import unshaded.io.opentelemetry.metrics.BatchRecorder;
import unshaded.io.opentelemetry.metrics.DoubleCounter;
import unshaded.io.opentelemetry.metrics.DoubleSumObserver;
import unshaded.io.opentelemetry.metrics.DoubleUpDownCounter;
import unshaded.io.opentelemetry.metrics.DoubleUpDownSumObserver;
import unshaded.io.opentelemetry.metrics.DoubleValueObserver;
import unshaded.io.opentelemetry.metrics.DoubleValueRecorder;
import unshaded.io.opentelemetry.metrics.LongCounter;
import unshaded.io.opentelemetry.metrics.LongSumObserver;
import unshaded.io.opentelemetry.metrics.LongUpDownCounter;
import unshaded.io.opentelemetry.metrics.LongUpDownSumObserver;
import unshaded.io.opentelemetry.metrics.LongValueObserver;
import unshaded.io.opentelemetry.metrics.LongValueRecorder;
import unshaded.io.opentelemetry.metrics.Meter;

class UnshadedMeter implements Meter {

  private final io.opentelemetry.metrics.Meter shadedMeter;

  UnshadedMeter(final io.opentelemetry.metrics.Meter shadedMeter) {
    this.shadedMeter = shadedMeter;
  }

  @Override
  public DoubleCounter.Builder doubleCounterBuilder(final String name) {
    return new UnshadedDoubleCounter.Builder(shadedMeter.doubleCounterBuilder(name));
  }

  @Override
  public LongCounter.Builder longCounterBuilder(final String name) {
    return new UnshadedLongCounter.Builder(shadedMeter.longCounterBuilder(name));
  }

  @Override
  public DoubleUpDownCounter.Builder doubleUpDownCounterBuilder(String s) {
    // TODO https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/463
    return null;
  }

  @Override
  public LongUpDownCounter.Builder longUpDownCounterBuilder(String s) {
    // TODO https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/463
    return null;
  }

  @Override
  public DoubleValueRecorder.Builder doubleValueRecorderBuilder(final String name) {
    return new UnshadedDoubleValueRecorder.Builder(shadedMeter.doubleValueRecorderBuilder(name));
  }

  @Override
  public LongValueRecorder.Builder longValueRecorderBuilder(final String name) {
    return new UnshadedLongValueRecorder.Builder(shadedMeter.longValueRecorderBuilder(name));
  }

  @Override
  public DoubleSumObserver.Builder doubleSumObserverBuilder(String s) {
    // TODO https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/463
    return null;
  }

  @Override
  public LongSumObserver.Builder longSumObserverBuilder(String s) {
    // TODO https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/463
    return null;
  }

  @Override
  public DoubleUpDownSumObserver.Builder doubleUpDownSumObserverBuilder(String s) {
    // TODO https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/463
    return null;
  }

  @Override
  public LongUpDownSumObserver.Builder longUpDownSumObserverBuilder(String s) {
    // TODO https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/463
    return null;
  }

  @Override
  public DoubleValueObserver.Builder doubleValueObserverBuilder(final String name) {
    return new UnshadedDoubleValueObserver.Builder(shadedMeter.doubleValueObserverBuilder(name));
  }

  @Override
  public LongValueObserver.Builder longValueObserverBuilder(final String name) {
    return new UnshadedLongValueObserver.Builder(shadedMeter.longValueObserverBuilder(name));
  }

  @Override
  public BatchRecorder newBatchRecorder(final String... keyValuePairs) {
    return new UnshadedBatchRecorder(shadedMeter.newBatchRecorder(keyValuePairs));
  }
}
