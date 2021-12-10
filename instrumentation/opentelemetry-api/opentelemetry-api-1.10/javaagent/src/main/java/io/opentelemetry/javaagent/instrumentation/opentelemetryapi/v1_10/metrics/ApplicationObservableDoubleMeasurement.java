/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics;

import application.io.opentelemetry.api.common.Attributes;
import application.io.opentelemetry.api.metrics.ObservableDoubleMeasurement;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.Bridging;

final class ApplicationObservableDoubleMeasurement implements ObservableDoubleMeasurement {

  private final io.opentelemetry.api.metrics.ObservableDoubleMeasurement agentMeasurement;

  ApplicationObservableDoubleMeasurement(
      io.opentelemetry.api.metrics.ObservableDoubleMeasurement agentMeasurement) {
    this.agentMeasurement = agentMeasurement;
  }

  @Override
  public void record(double v) {
    agentMeasurement.record(v);
  }

  @Override
  public void record(double v, Attributes attributes) {
    agentMeasurement.record(v, Bridging.toAgent(attributes));
  }
}
