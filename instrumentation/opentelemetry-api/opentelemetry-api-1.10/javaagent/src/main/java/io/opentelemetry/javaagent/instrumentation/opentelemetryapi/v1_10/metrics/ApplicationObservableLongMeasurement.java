/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics;

import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.Bridging;

final class ApplicationObservableLongMeasurement
    implements application.io.opentelemetry.api.metrics.ObservableLongMeasurement,
        ObservableMeasurementWrapper<ObservableLongMeasurement> {

  private final ObservableLongMeasurement agentMeasurement;

  ApplicationObservableLongMeasurement(ObservableLongMeasurement agentMeasurement) {
    this.agentMeasurement = agentMeasurement;
  }

  @Override
  public void record(long v) {
    agentMeasurement.record(v);
  }

  @Override
  public void record(long v, application.io.opentelemetry.api.common.Attributes attributes) {
    agentMeasurement.record(v, Bridging.toAgent(attributes));
  }

  @Override
  public ObservableLongMeasurement unwrap() {
    return agentMeasurement;
  }
}
