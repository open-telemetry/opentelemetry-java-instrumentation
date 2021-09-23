/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.metrics.bridge;

import application.io.opentelemetry.api.metrics.MeterBuilder;
import application.io.opentelemetry.api.metrics.MeterProvider;

// Our convention for accessing agent packages.
@SuppressWarnings("UnnecessarilyFullyQualified")
public class ApplicationMeterProvider implements MeterProvider {

  public static final MeterProvider INSTANCE = new ApplicationMeterProvider();

  private final io.opentelemetry.api.metrics.MeterProvider agentMeterProvider;

  public ApplicationMeterProvider() {
    this.agentMeterProvider = io.opentelemetry.api.metrics.GlobalMeterProvider.get();
  }

  @Override
  public MeterBuilder meterBuilder(String instrumentationName) {
    return new ApplicationMeterBuilder(agentMeterProvider.meterBuilder(instrumentationName));
  }
}
