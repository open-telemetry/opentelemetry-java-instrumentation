/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics;

import io.opentelemetry.api.metrics.ObservableDoubleMeasurement;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.Bridging;

final class ApplicationObservableDoubleMeasurement
    implements application.io.opentelemetry.api.metrics.ObservableDoubleMeasurement,
        ObservableMeasurementWrapper<ObservableDoubleMeasurement> {

  private final ObservableDoubleMeasurement agentMeasurement;

  ApplicationObservableDoubleMeasurement(ObservableDoubleMeasurement agentMeasurement) {
    this.agentMeasurement = agentMeasurement;
  }

  @Override
  public void record(double v) {
    agentMeasurement.record(v);
  }

  @Override
  public void record(double v, application.io.opentelemetry.api.common.Attributes attributes) {
    agentMeasurement.record(v, Bridging.toAgent(attributes));
  }

  @Override
  public ObservableDoubleMeasurement unwrap() {
    return agentMeasurement;
  }
}
