/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_65.incubator.metrics;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongGauge;

// Compatibility implementation; bound metric forwarding is added by the follow-up bridge.
final class ApplicationBoundLongGauge165Incubator
    implements application.io.opentelemetry.api.incubator.metrics.BoundLongGauge {

  ApplicationBoundLongGauge165Incubator(LongGauge agentGauge, Attributes attributes) {}

  @Override
  public void set(long value) {}

  @Override
  public void set(long value, application.io.opentelemetry.context.Context applicationContext) {}
}
