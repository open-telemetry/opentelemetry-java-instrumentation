/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import io.opentelemetry.api.OpenTelemetry;

/** A builder of {@link AwsSdkTracing}. */
public final class AwsSdkTracingBuilder {

  private final OpenTelemetry openTelemetry;

  private boolean captureExperimentalSpanAttributes;

  AwsSdkTracingBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Sets whether experimental attributes should be set to spans. These attributes may be changed or
   * removed in the future, so only enable this if you know you do not require attributes filled by
   * this instrumentation to be stable across versions
   */
  public AwsSdkTracingBuilder setCaptureExperimentalSpanAttributes(
      boolean captureExperimentalSpanAttributes) {
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
    return this;
  }

  /** Returns a new {@link AwsSdkTracing} with the settings of this {@link AwsSdkTracingBuilder}. */
  public AwsSdkTracing build() {
    return new AwsSdkTracing(openTelemetry, captureExperimentalSpanAttributes);
  }
}
