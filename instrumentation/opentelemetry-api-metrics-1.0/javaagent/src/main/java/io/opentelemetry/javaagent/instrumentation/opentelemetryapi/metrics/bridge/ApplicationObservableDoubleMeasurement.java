/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.metrics.bridge;

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
  public void observe(double value) {
    agentMeasurement.observe(value);
  }

  @Override
  public void observe(double value, Attributes attributes) {
    agentMeasurement.observe(value, Bridging.toAgent(attributes));
  }
}
