/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics;

import application.io.opentelemetry.api.metrics.Meter;
import application.io.opentelemetry.api.metrics.MeterBuilder;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

final class ApplicationMeterBuilder implements MeterBuilder {

  private final ApplicationMeterFactory meterFactory;
  private final io.opentelemetry.api.metrics.MeterBuilder agentBuilder;

  ApplicationMeterBuilder(
      ApplicationMeterFactory meterFactory,
      io.opentelemetry.api.metrics.MeterBuilder agentBuilder) {
    this.meterFactory = meterFactory;
    this.agentBuilder = agentBuilder;
  }

  @Override
  @CanIgnoreReturnValue
  public MeterBuilder setSchemaUrl(String schemaUrl) {
    agentBuilder.setSchemaUrl(schemaUrl);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public MeterBuilder setInstrumentationVersion(String version) {
    agentBuilder.setInstrumentationVersion(version);
    return this;
  }

  @Override
  public Meter build() {
    return meterFactory.newMeter(agentBuilder.build());
  }
}
