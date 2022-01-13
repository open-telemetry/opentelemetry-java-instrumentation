/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.micrometer.v1_5;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.micrometer.v1_5.OpenTelemetryMeterRegistry;

public final class MicrometerSingletons {

  private static final MeterRegistry METER_REGISTRY =
      OpenTelemetryMeterRegistry.create(GlobalOpenTelemetry.get());

  public static MeterRegistry meterRegistry() {
    return METER_REGISTRY;
  }

  private MicrometerSingletons() {}
}
