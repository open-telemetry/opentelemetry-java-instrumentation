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

import lombok.extern.slf4j.Slf4j;
import unshaded.io.opentelemetry.metrics.BatchRecorder;
import unshaded.io.opentelemetry.metrics.DoubleCounter;
import unshaded.io.opentelemetry.metrics.DoubleUpDownCounter;
import unshaded.io.opentelemetry.metrics.DoubleValueRecorder;
import unshaded.io.opentelemetry.metrics.LongCounter;
import unshaded.io.opentelemetry.metrics.LongUpDownCounter;
import unshaded.io.opentelemetry.metrics.LongValueRecorder;

@Slf4j
class UnshadedBatchRecorder implements BatchRecorder {

  private final io.opentelemetry.metrics.BatchRecorder shadedBatchRecorder;

  UnshadedBatchRecorder(final io.opentelemetry.metrics.BatchRecorder shadedBatchRecorder) {
    this.shadedBatchRecorder = shadedBatchRecorder;
  }

  @Override
  public BatchRecorder put(final LongValueRecorder measure, final long value) {
    if (measure instanceof UnshadedLongValueRecorder) {
      shadedBatchRecorder.put(
          ((UnshadedLongValueRecorder) measure).getShadedLongValueRecorder(), value);
    } else {
      log.debug("unexpected measure: {}", measure);
    }
    return this;
  }

  @Override
  public BatchRecorder put(final DoubleValueRecorder measure, final double value) {
    if (measure instanceof UnshadedDoubleValueRecorder) {
      shadedBatchRecorder.put(
          ((UnshadedDoubleValueRecorder) measure).getShadedDoubleValueRecorder(), value);
    } else {
      log.debug("unexpected measure: {}", measure);
    }
    return this;
  }

  @Override
  public BatchRecorder put(final LongCounter counter, final long value) {
    if (counter instanceof UnshadedLongCounter) {
      shadedBatchRecorder.put(((UnshadedLongCounter) counter).getShadedLongCounter(), value);
    } else {
      log.debug("unexpected counter: {}", counter);
    }
    return this;
  }

  @Override
  public BatchRecorder put(final DoubleCounter counter, final double value) {
    if (counter instanceof UnshadedDoubleCounter) {
      shadedBatchRecorder.put(((UnshadedDoubleCounter) counter).getShadedDoubleCounter(), value);
    } else {
      log.debug("unexpected counter: {}", counter);
    }
    return this;
  }

  @Override
  public BatchRecorder put(LongUpDownCounter longUpDownCounter, long l) {
    // TODO https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/463
    return null;
  }

  @Override
  public BatchRecorder put(DoubleUpDownCounter doubleUpDownCounter, double v) {
    // TODO https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/463
    return null;
  }

  @Override
  public void record() {
    shadedBatchRecorder.record();
  }
}
