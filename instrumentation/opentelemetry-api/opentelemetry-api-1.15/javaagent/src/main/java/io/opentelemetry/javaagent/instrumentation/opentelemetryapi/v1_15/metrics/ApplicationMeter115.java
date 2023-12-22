/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_15.metrics;

import application.io.opentelemetry.api.metrics.BatchCallback;
import application.io.opentelemetry.api.metrics.ObservableMeasurement;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics.ApplicationMeter;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics.ObservableMeasurementWrapper;

public class ApplicationMeter115 extends ApplicationMeter {

  private final io.opentelemetry.api.metrics.Meter agentMeter;

  protected ApplicationMeter115(io.opentelemetry.api.metrics.Meter agentMeter) {
    super(agentMeter);
    this.agentMeter = agentMeter;
  }

  @Override
  public BatchCallback batchCallback(
      Runnable callback,
      ObservableMeasurement observableMeasurement,
      ObservableMeasurement... additionalMeasurements) {
    return new ApplicationBatchCallback(
        agentMeter.batchCallback(
            callback, unwrap(observableMeasurement), unwrap(additionalMeasurements)));
  }

  @SuppressWarnings("unchecked")
  private static io.opentelemetry.api.metrics.ObservableMeasurement unwrap(
      ObservableMeasurement observableMeasurement) {
    if (observableMeasurement == null) {
      return null;
    }
    if (!(observableMeasurement instanceof ObservableMeasurementWrapper)) {
      // unwrap instruments that weren't created by us into a dummy instrument
      // sdk ignores instruments that it didn't create
      return new io.opentelemetry.api.metrics.ObservableMeasurement() {};
    }
    return ((ObservableMeasurementWrapper<io.opentelemetry.api.metrics.ObservableMeasurement>)
            observableMeasurement)
        .unwrap();
  }

  private static io.opentelemetry.api.metrics.ObservableMeasurement[] unwrap(
      ObservableMeasurement[] observableMeasurements) {
    if (observableMeasurements == null) {
      return null;
    }
    io.opentelemetry.api.metrics.ObservableMeasurement[] result =
        new io.opentelemetry.api.metrics.ObservableMeasurement[observableMeasurements.length];
    for (int i = 0; i < observableMeasurements.length; i++) {
      result[i] = unwrap(observableMeasurements[i]);
    }
    return result;
  }
}
