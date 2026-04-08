/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics;

import io.opentelemetry.api.metrics.MeterProvider;

public class ApplicationMeterProvider
    implements application.io.opentelemetry.api.metrics.MeterProvider {

  private final ApplicationMeterFactory meterFactory;
  private final MeterProvider agentMeterProvider;

  public ApplicationMeterProvider(
      ApplicationMeterFactory meterFactory, MeterProvider agentMeterProvider) {
    this.meterFactory = meterFactory;
    this.agentMeterProvider = agentMeterProvider;
  }

  @Override
  public application.io.opentelemetry.api.metrics.MeterBuilder meterBuilder(
      String instrumentationName) {
    return new ApplicationMeterBuilder(
        meterFactory, agentMeterProvider.meterBuilder(instrumentationName));
  }
}
