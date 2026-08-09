/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_65.incubator.metrics;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;

// Compatibility implementation; bound metric forwarding is added by the follow-up bridge.
final class ApplicationBoundLongCounter165Incubator
    implements application.io.opentelemetry.api.incubator.metrics.BoundLongCounter {

  ApplicationBoundLongCounter165Incubator(LongCounter agentCounter, Attributes attributes) {}

  @Override
  public void add(long value) {}

  @Override
  public void add(long value, application.io.opentelemetry.context.Context applicationContext) {}
}
