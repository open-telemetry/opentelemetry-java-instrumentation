/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmq;

import io.opentelemetry.api.OpenTelemetry;

public final class RocketMqTracingBuilder {

  private final OpenTelemetry openTelemetry;

  private boolean captureExperimentalSpanAttributes;
  private boolean propagationEnabled;

  RocketMqTracingBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  public RocketMqTracingBuilder setCaptureExperimentalSpanAttributes(
      boolean captureExperimentalSpanAttributes) {
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
    return this;
  }

  public RocketMqTracingBuilder setPropagationEnabled(boolean propagationEnabled) {
    this.propagationEnabled = propagationEnabled;
    return this;
  }

  public RocketMqTracing build() {
    return new RocketMqTracing(
        openTelemetry, captureExperimentalSpanAttributes, propagationEnabled);
  }
}
