/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterBuilder;
import io.opentelemetry.instrumentation.api.internal.EmbeddedInstrumentationProperties;
import javax.annotation.Nullable;

class RuntimeMetricsUtil {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.runtime-metrics";

  @Nullable
  private static final String INSTRUMENTATION_VERSION =
      EmbeddedInstrumentationProperties.findVersion(INSTRUMENTATION_NAME);

  static Meter getMeter(OpenTelemetry openTelemetry) {
    MeterBuilder meterBuilder = openTelemetry.meterBuilder(INSTRUMENTATION_NAME);
    if (INSTRUMENTATION_VERSION != null) {
      meterBuilder.setInstrumentationVersion(INSTRUMENTATION_VERSION);
    }
    return meterBuilder.build();
  }

  private RuntimeMetricsUtil() {}
}
