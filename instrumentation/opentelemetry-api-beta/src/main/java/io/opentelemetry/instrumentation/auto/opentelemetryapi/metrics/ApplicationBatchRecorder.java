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

package io.opentelemetry.instrumentation.auto.opentelemetryapi.metrics;

import application.io.opentelemetry.metrics.BatchRecorder;
import application.io.opentelemetry.metrics.DoubleCounter;
import application.io.opentelemetry.metrics.DoubleUpDownCounter;
import application.io.opentelemetry.metrics.DoubleValueRecorder;
import application.io.opentelemetry.metrics.LongCounter;
import application.io.opentelemetry.metrics.LongUpDownCounter;
import application.io.opentelemetry.metrics.LongValueRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ApplicationBatchRecorder implements BatchRecorder {

  private static final Logger log = LoggerFactory.getLogger(ApplicationBatchRecorder.class);

  private final io.opentelemetry.metrics.BatchRecorder agentBatchRecorder;

  ApplicationBatchRecorder(final io.opentelemetry.metrics.BatchRecorder agentBatchRecorder) {
    this.agentBatchRecorder = agentBatchRecorder;
  }

  @Override
  public BatchRecorder put(final LongValueRecorder measure, final long value) {
    if (measure instanceof ApplicationLongValueRecorder) {
      agentBatchRecorder.put(
          ((ApplicationLongValueRecorder) measure).getAgentLongValueRecorder(), value);
    } else {
      log.debug("unexpected measure: {}", measure);
    }
    return this;
  }

  @Override
  public BatchRecorder put(final DoubleValueRecorder measure, final double value) {
    if (measure instanceof ApplicationDoubleValueRecorder) {
      agentBatchRecorder.put(
          ((ApplicationDoubleValueRecorder) measure).getAgentDoubleValueRecorder(), value);
    } else {
      log.debug("unexpected measure: {}", measure);
    }
    return this;
  }

  @Override
  public BatchRecorder put(final LongCounter counter, final long value) {
    if (counter instanceof ApplicationLongCounter) {
      agentBatchRecorder.put(((ApplicationLongCounter) counter).getAgentLongCounter(), value);
    } else {
      log.debug("unexpected counter: {}", counter);
    }
    return this;
  }

  @Override
  public BatchRecorder put(final DoubleCounter counter, final double value) {
    if (counter instanceof ApplicationDoubleCounter) {
      agentBatchRecorder.put(((ApplicationDoubleCounter) counter).getAgentDoubleCounter(), value);
    } else {
      log.debug("unexpected counter: {}", counter);
    }
    return this;
  }

  @Override
  public BatchRecorder put(final LongUpDownCounter counter, final long value) {
    if (counter instanceof ApplicationLongUpDownCounter) {
      agentBatchRecorder.put(
          ((ApplicationLongUpDownCounter) counter).getAgentLongUpDownCounter(), value);
    } else {
      log.debug("unexpected counter: {}", counter);
    }
    return this;
  }

  @Override
  public BatchRecorder put(final DoubleUpDownCounter counter, final double value) {
    if (counter instanceof ApplicationDoubleUpDownCounter) {
      agentBatchRecorder.put(
          ((ApplicationDoubleUpDownCounter) counter).getAgentDoubleUpDownCounter(), value);
    } else {
      log.debug("unexpected counter: {}", counter);
    }
    return this;
  }

  @Override
  public void record() {
    agentBatchRecorder.record();
  }
}
