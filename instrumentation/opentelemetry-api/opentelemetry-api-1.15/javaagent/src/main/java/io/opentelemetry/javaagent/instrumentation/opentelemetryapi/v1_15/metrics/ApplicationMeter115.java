/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_15.metrics;

import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableMeasurement;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics.ApplicationMeter;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics.CallbackAnchor;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics.ObservableMeasurementWrapper;

public class ApplicationMeter115 extends ApplicationMeter {

  private final Meter agentMeter;

  protected ApplicationMeter115(Meter agentMeter) {
    super(agentMeter);
    this.agentMeter = agentMeter;
  }

  @Override
  public application.io.opentelemetry.api.metrics.BatchCallback batchCallback(
      Runnable callback,
      application.io.opentelemetry.api.metrics.ObservableMeasurement observableMeasurement,
      application.io.opentelemetry.api.metrics.ObservableMeasurement... additionalMeasurements) {
    return new ApplicationBatchCallback(
        CallbackAnchor.anchorBatch(
            weak ->
                agentMeter.batchCallback(
                    weak, unwrap(observableMeasurement), unwrap(additionalMeasurements)),
            callback));
  }

  private static ObservableMeasurement unwrap(
      application.io.opentelemetry.api.metrics.ObservableMeasurement observableMeasurement) {
    if (observableMeasurement == null) {
      return null;
    }
    if (!(observableMeasurement instanceof ObservableMeasurementWrapper)) {
      // unwrap instruments that weren't created by us into a dummy instrument
      // sdk ignores instruments that it didn't create
      return new ObservableMeasurement() {};
    }
    return ((ObservableMeasurementWrapper<?>) observableMeasurement).unwrap();
  }

  private static ObservableMeasurement[] unwrap(
      application.io.opentelemetry.api.metrics.ObservableMeasurement[] observableMeasurements) {
    if (observableMeasurements == null) {
      return null;
    }
    ObservableMeasurement[] result = new ObservableMeasurement[observableMeasurements.length];
    for (int i = 0; i < observableMeasurements.length; i++) {
      result[i] = unwrap(observableMeasurements[i]);
    }
    return result;
  }
}
