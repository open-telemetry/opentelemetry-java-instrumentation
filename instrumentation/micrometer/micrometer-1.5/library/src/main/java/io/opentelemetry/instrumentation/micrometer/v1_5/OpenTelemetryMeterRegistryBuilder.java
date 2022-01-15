/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.OpenTelemetry;

/** A builder of {@link OpenTelemetryMeterRegistry}. */
public final class OpenTelemetryMeterRegistryBuilder {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.micrometer-1.5";

  private final OpenTelemetry openTelemetry;

  OpenTelemetryMeterRegistryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Returns a new {@link OpenTelemetryMeterRegistry} with the settings of this {@link
   * OpenTelemetryMeterRegistryBuilder}.
   */
  public MeterRegistry build() {
    return new OpenTelemetryMeterRegistry(
        Clock.SYSTEM, openTelemetry.getMeterProvider().get(INSTRUMENTATION_NAME));
  }
}
