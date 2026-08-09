/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_65.incubator.metrics;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleUpDownCounter;

// Compatibility implementation; bound metric forwarding is added by the follow-up bridge.
final class ApplicationBoundDoubleUpDownCounter165Incubator
    implements application.io.opentelemetry.api.incubator.metrics.BoundDoubleUpDownCounter {

  ApplicationBoundDoubleUpDownCounter165Incubator(
      DoubleUpDownCounter agentCounter, Attributes attributes) {}

  @Override
  public void add(double value) {}

  @Override
  public void add(double value, application.io.opentelemetry.context.Context applicationContext) {}
}
