/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmq;

import io.opentelemetry.api.OpenTelemetry;

/** A builder of {@link RocketMqTracing}. */
public final class RocketMqTracingBuilder {

  private final OpenTelemetry openTelemetry;

  private boolean captureExperimentalSpanAttributes;
  private boolean propagationEnabled;

  RocketMqTracingBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Sets whether experimental attributes should be set to spans. These attributes may be changed or
   * removed in the future, so only enable this if you know you do not require attributes filled by
   * this instrumentation to be stable across versions
   */
  public RocketMqTracingBuilder setCaptureExperimentalSpanAttributes(
      boolean captureExperimentalSpanAttributes) {
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
    return this;
  }

  public RocketMqTracingBuilder setPropagationEnabled(boolean propagationEnabled) {
    this.propagationEnabled = propagationEnabled;
    return this;
  }

  /** Returns a new {@link RocketMqTracing} with the settings of this {@link RocketMqTracingBuilder}. */
  public RocketMqTracing build() {
    return new RocketMqTracing(
        openTelemetry, captureExperimentalSpanAttributes, propagationEnabled);
  }
}
