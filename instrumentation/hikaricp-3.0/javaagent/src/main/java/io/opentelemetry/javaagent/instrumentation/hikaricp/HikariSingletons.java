/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hikaricp;

import com.zaxxer.hikari.metrics.MetricsTrackerFactory;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.hikaricp.HikariTelemetry;
import javax.annotation.Nullable;

public final class HikariSingletons {

  private static final HikariTelemetry hikariTelemetry =
      HikariTelemetry.create(GlobalOpenTelemetry.get());

  public static MetricsTrackerFactory createMetricsTrackerFactory(
      @Nullable MetricsTrackerFactory delegate) {
    return hikariTelemetry.createMetricsTrackerFactory(delegate);
  }

  private HikariSingletons() {}
}
