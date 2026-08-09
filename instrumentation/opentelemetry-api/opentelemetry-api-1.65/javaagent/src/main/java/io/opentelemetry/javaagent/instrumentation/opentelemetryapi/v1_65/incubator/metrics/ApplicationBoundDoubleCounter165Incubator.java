/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_65.incubator.metrics;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleCounter;

// Compatibility implementation; bound metric forwarding is added by the follow-up bridge.
final class ApplicationBoundDoubleCounter165Incubator
    implements application.io.opentelemetry.api.incubator.metrics.BoundDoubleCounter {

  ApplicationBoundDoubleCounter165Incubator(DoubleCounter agentCounter, Attributes attributes) {}

  @Override
  public void add(double value) {}

  @Override
  public void add(double value, application.io.opentelemetry.context.Context applicationContext) {}
}
