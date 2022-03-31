/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import io.opentelemetry.api.OpenTelemetry;

/** A builder of {@link AwsSdkTelemetry}. */
public class AwsSdkTelemetryBuilder {

  private final OpenTelemetry openTelemetry;

  private boolean captureExperimentalSpanAttributes;

  AwsSdkTelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Sets whether experimental attributes should be set to spans. These attributes may be changed or
   * removed in the future, so only enable this if you know you do not require attributes filled by
   * this instrumentation to be stable across versions
   */
  public AwsSdkTelemetryBuilder setCaptureExperimentalSpanAttributes(
      boolean captureExperimentalSpanAttributes) {
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
    return this;
  }

  /**
   * Returns a new {@link AwsSdkTelemetry} with the settings of this {@link AwsSdkTelemetryBuilder}.
   */
  public AwsSdkTelemetry build() {
    return new AwsSdkTelemetry(openTelemetry, captureExperimentalSpanAttributes);
  }
}
