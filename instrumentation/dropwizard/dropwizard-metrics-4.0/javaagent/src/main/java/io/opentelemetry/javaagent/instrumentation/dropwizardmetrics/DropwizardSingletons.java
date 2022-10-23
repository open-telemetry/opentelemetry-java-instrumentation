/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.dropwizardmetrics;

import io.opentelemetry.api.GlobalOpenTelemetry;

public final class DropwizardSingletons {

  private static final DropwizardMetricsAdapter METRICS =
      new DropwizardMetricsAdapter(GlobalOpenTelemetry.get());

  public static DropwizardMetricsAdapter metrics() {
    return METRICS;
  }

  private DropwizardSingletons() {}
}
