/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.metrics;

import application.io.opentelemetry.metrics.Meter;
import application.io.opentelemetry.metrics.MeterProvider;

public class ApplicationMeterProvider implements MeterProvider {

  @Override
  public Meter get(String instrumentationName) {
    return new ApplicationMeter(
        io.opentelemetry.OpenTelemetry.getMeterProvider().get(instrumentationName));
  }

  @Override
  public Meter get(String instrumentationName, String instrumentationVersion) {
    return new ApplicationMeter(
        io.opentelemetry.OpenTelemetry.getMeterProvider()
            .get(instrumentationName, instrumentationVersion));
  }
}
