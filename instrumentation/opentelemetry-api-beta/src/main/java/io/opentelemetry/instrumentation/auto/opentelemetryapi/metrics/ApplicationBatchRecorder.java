/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
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

  ApplicationBatchRecorder(io.opentelemetry.metrics.BatchRecorder agentBatchRecorder) {
    this.agentBatchRecorder = agentBatchRecorder;
  }

  @Override
  public BatchRecorder put(LongValueRecorder measure, long value) {
    if (measure instanceof ApplicationLongValueRecorder) {
      agentBatchRecorder.put(
          ((ApplicationLongValueRecorder) measure).getAgentLongValueRecorder(), value);
    } else {
      log.debug("unexpected measure: {}", measure);
    }
    return this;
  }

  @Override
  public BatchRecorder put(DoubleValueRecorder measure, double value) {
    if (measure instanceof ApplicationDoubleValueRecorder) {
      agentBatchRecorder.put(
          ((ApplicationDoubleValueRecorder) measure).getAgentDoubleValueRecorder(), value);
    } else {
      log.debug("unexpected measure: {}", measure);
    }
    return this;
  }

  @Override
  public BatchRecorder put(LongCounter counter, long value) {
    if (counter instanceof ApplicationLongCounter) {
      agentBatchRecorder.put(((ApplicationLongCounter) counter).getAgentLongCounter(), value);
    } else {
      log.debug("unexpected counter: {}", counter);
    }
    return this;
  }

  @Override
  public BatchRecorder put(DoubleCounter counter, double value) {
    if (counter instanceof ApplicationDoubleCounter) {
      agentBatchRecorder.put(((ApplicationDoubleCounter) counter).getAgentDoubleCounter(), value);
    } else {
      log.debug("unexpected counter: {}", counter);
    }
    return this;
  }

  @Override
  public BatchRecorder put(LongUpDownCounter counter, long value) {
    if (counter instanceof ApplicationLongUpDownCounter) {
      agentBatchRecorder.put(
          ((ApplicationLongUpDownCounter) counter).getAgentLongUpDownCounter(), value);
    } else {
      log.debug("unexpected counter: {}", counter);
    }
    return this;
  }

  @Override
  public BatchRecorder put(DoubleUpDownCounter counter, double value) {
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
