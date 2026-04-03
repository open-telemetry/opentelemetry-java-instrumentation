/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.metrics.Meter;

/**
 * Helper loaded by a child-first classloader in {@link CallbackGcCloseTest} to register an
 * observable gauge from a separate classloader's copy of the OpenTelemetry API.
 *
 * <p>When this class is loaded by a child-first classloader, its references to {@code
 * io.opentelemetry.api.*} resolve to the child classloader's copies. The agent instruments those
 * copies and injects bridge helpers into the child classloader, including {@link CallbackAnchor}
 * with its own static {@code callbacks} list anchored to the child classloader's lifecycle.
 */
public final class GaugeRegistrar {

  public static void register(String instrumentationName, String gaugeName) {
    Meter meter =
        GlobalOpenTelemetry.get().getMeterProvider().meterBuilder(instrumentationName).build();
    meter.gaugeBuilder(gaugeName).ofLongs().buildWithCallback(m -> m.record(42));
  }

  private GaugeRegistrar() {}
}
