/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics;

import application.io.opentelemetry.api.metrics.MeterBuilder;
import application.io.opentelemetry.api.metrics.MeterProvider;

// Our convention for accessing agent packages.
@SuppressWarnings("UnnecessarilyFullyQualified")
public class ApplicationMeterProvider implements MeterProvider {

  private final ApplicationMeterFactory meterFactory;
  private final io.opentelemetry.api.metrics.MeterProvider agentMeterProvider;

  public ApplicationMeterProvider(
      ApplicationMeterFactory meterFactory,
      io.opentelemetry.api.metrics.MeterProvider agentMeterProvider) {
    this.meterFactory = meterFactory;
    this.agentMeterProvider = agentMeterProvider;
  }

  @Override
  public MeterBuilder meterBuilder(String instrumentationName) {
    return new ApplicationMeterBuilder(
        meterFactory, agentMeterProvider.meterBuilder(instrumentationName));
  }
}
