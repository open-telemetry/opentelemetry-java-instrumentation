/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.metrics.MeterBuilder;

final class ApplicationMeterBuilder
    implements application.io.opentelemetry.api.metrics.MeterBuilder {

  private final ApplicationMeterFactory meterFactory;
  private final MeterBuilder agentBuilder;

  ApplicationMeterBuilder(ApplicationMeterFactory meterFactory, MeterBuilder agentBuilder) {
    this.meterFactory = meterFactory;
    this.agentBuilder = agentBuilder;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.metrics.MeterBuilder setSchemaUrl(String schemaUrl) {
    agentBuilder.setSchemaUrl(schemaUrl);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.metrics.MeterBuilder setInstrumentationVersion(
      String version) {
    agentBuilder.setInstrumentationVersion(version);
    return this;
  }

  @Override
  public application.io.opentelemetry.api.metrics.Meter build() {
    return meterFactory.newMeter(agentBuilder.build());
  }
}
