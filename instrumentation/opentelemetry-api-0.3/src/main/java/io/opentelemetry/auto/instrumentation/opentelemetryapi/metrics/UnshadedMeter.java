/*
 * Copyright 2020, OpenTelemetry Authors
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

import java.util.Map;
import unshaded.io.opentelemetry.metrics.BatchRecorder;
import unshaded.io.opentelemetry.metrics.DoubleCounter;
import unshaded.io.opentelemetry.metrics.DoubleMeasure;
import unshaded.io.opentelemetry.metrics.DoubleObserver;
import unshaded.io.opentelemetry.metrics.LabelSet;
import unshaded.io.opentelemetry.metrics.LongCounter;
import unshaded.io.opentelemetry.metrics.LongMeasure;
import unshaded.io.opentelemetry.metrics.LongObserver;
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
  public DoubleMeasure.Builder doubleMeasureBuilder(final String name) {
    return new UnshadedDoubleMeasure.Builder(shadedMeter.doubleMeasureBuilder(name));
  }

  @Override
  public LongMeasure.Builder longMeasureBuilder(final String name) {
    return new UnshadedLongMeasure.Builder(shadedMeter.longMeasureBuilder(name));
  }

  @Override
  public DoubleObserver.Builder doubleObserverBuilder(final String name) {
    return new UnshadedDoubleObserver.Builder(shadedMeter.doubleObserverBuilder(name));
  }

  @Override
  public LongObserver.Builder longObserverBuilder(final String name) {
    return new UnshadedLongObserver.Builder(shadedMeter.longObserverBuilder(name));
  }

  @Override
  public BatchRecorder newBatchRecorder(final String... keyValuePairs) {
    return new UnshadedBatchRecorder(shadedMeter.newBatchRecorder(keyValuePairs));
  }

  @Override
  public LabelSet createLabelSet(final String... keyValuePairs) {
    // this is going away
    return null;
  }

  @Override
  public LabelSet createLabelSet(final Map<String, String> labels) {
    // this is going away
    return null;
  }
}
