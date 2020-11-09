/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.metrics;

import application.io.opentelemetry.api.metrics.Meter;
import application.io.opentelemetry.api.metrics.MeterProvider;

public class ApplicationMeterProvider implements MeterProvider {

  @Override
  public Meter get(String instrumentationName) {
    return new ApplicationMeter(
        io.opentelemetry.api.OpenTelemetry.getGlobalMeterProvider().get(instrumentationName));
  }

  @Override
  public Meter get(String instrumentationName, String instrumentationVersion) {
    return new ApplicationMeter(
        io.opentelemetry.api.OpenTelemetry.getGlobalMeterProvider()
            .get(instrumentationName, instrumentationVersion));
  }
}
