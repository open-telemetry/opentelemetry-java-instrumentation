/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_65.incubator.metrics;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;

// Compatibility implementation; bound metric forwarding is added by the follow-up bridge.
final class ApplicationBoundDoubleHistogram165Incubator
    implements application.io.opentelemetry.api.incubator.metrics.BoundDoubleHistogram {

  ApplicationBoundDoubleHistogram165Incubator(
      DoubleHistogram agentHistogram, Attributes attributes) {}

  @Override
  public void record(double value) {}

  @Override
  public void record(
      double value, application.io.opentelemetry.context.Context applicationContext) {}
}
