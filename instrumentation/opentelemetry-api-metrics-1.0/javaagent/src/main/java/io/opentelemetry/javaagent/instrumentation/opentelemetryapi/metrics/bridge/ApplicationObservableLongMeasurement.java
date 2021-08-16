/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.metrics.bridge;

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
  public void observe(long value) {
    agentMeasurement.observe(value);
  }

  @Override
  public void observe(long value, Attributes attributes) {
    agentMeasurement.observe(value, Bridging.toAgent(attributes));
  }
}
