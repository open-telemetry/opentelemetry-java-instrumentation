/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.java8.internal;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterBuilder;
import io.opentelemetry.instrumentation.api.internal.EmbeddedInstrumentationProperties;
import java.util.List;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class JmxRuntimeMetricsUtil {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.runtime-telemetry-java8";

  @Nullable
  private static final String INSTRUMENTATION_VERSION =
      EmbeddedInstrumentationProperties.findVersion(INSTRUMENTATION_NAME);

  public static Meter getMeter(OpenTelemetry openTelemetry) {
    MeterBuilder meterBuilder = openTelemetry.meterBuilder(INSTRUMENTATION_NAME);
    if (INSTRUMENTATION_VERSION != null) {
      meterBuilder.setInstrumentationVersion(INSTRUMENTATION_VERSION);
    }
    return meterBuilder.build();
  }

  public static String getInstrumentationName() {
    return INSTRUMENTATION_NAME;
  }

  @Nullable
  public static String getInstrumentationVersion() {
    return INSTRUMENTATION_VERSION;
  }

  public static void closeObservers(List<AutoCloseable> observables) {
    observables.forEach(
        observable -> {
          try {
            observable.close();
          } catch (Exception e) {
            // Ignore
          }
        });
  }

  private JmxRuntimeMetricsUtil() {}
}
