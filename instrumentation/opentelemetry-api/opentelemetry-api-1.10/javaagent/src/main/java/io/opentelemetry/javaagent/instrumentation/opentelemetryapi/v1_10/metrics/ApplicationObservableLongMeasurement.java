/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics;

import application.io.opentelemetry.api.common.Attributes;
import application.io.opentelemetry.api.metrics.ObservableLongMeasurement;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.Bridging;

final class ApplicationObservableLongMeasurement implements ObservableLongMeasurement {

  private final io.opentelemetry.api.metrics.ObservableLongMeasurement agentMeasurement;

  ApplicationObservableLongMeasurement(
      io.opentelemetry.api.metrics.ObservableLongMeasurement agentMeasurement) {
    this.agentMeasurement = agentMeasurement;
  }

  @Override
  public void record(long v) {
    agentMeasurement.record(v);
  }

  @Override
  public void record(long v, Attributes attributes) {
    agentMeasurement.record(v, Bridging.toAgent(attributes));
  }
}
