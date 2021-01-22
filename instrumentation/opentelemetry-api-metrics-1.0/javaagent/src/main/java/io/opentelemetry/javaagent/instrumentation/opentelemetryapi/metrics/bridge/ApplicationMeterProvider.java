/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.metrics.bridge;

import application.io.opentelemetry.api.metrics.Meter;
import application.io.opentelemetry.api.metrics.MeterProvider;

public class ApplicationMeterProvider implements MeterProvider {

  public static final MeterProvider INSTANCE = new ApplicationMeterProvider();

  private final io.opentelemetry.api.metrics.MeterProvider agentMeterProvider;

  public ApplicationMeterProvider() {
    this.agentMeterProvider = io.opentelemetry.api.metrics.GlobalMetricsProvider.get();
  }

  @Override
  public Meter get(String instrumentationName) {
    return new ApplicationMeter(agentMeterProvider.get(instrumentationName));
  }

  @Override
  public Meter get(String instrumentationName, String instrumentationVersion) {
    return new ApplicationMeter(
        agentMeterProvider.get(instrumentationName, instrumentationVersion));
  }
}
