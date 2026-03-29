/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import static java.util.Collections.emptyList;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** A builder of {@link RocketMqTelemetry}. */
public final class RocketMqTelemetryBuilder {

  private final OpenTelemetry openTelemetry;

  private List<String> capturedHeaders = emptyList();
  private boolean captureExperimentalSpanAttributes;

  RocketMqTelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Sets whether experimental attributes should be set to spans. These attributes may be changed or
   * removed in the future, so only enable this if you know you do not require attributes filled by
   * this instrumentation to be stable across versions
   */
  @CanIgnoreReturnValue
  public RocketMqTelemetryBuilder setCaptureExperimentalSpanAttributes(
      boolean captureExperimentalSpanAttributes) {
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
    return this;
  }

  /**
   * Configures the messaging headers that will be captured as span attributes.
   *
   * @param capturedHeaders A list of messaging header names.
   */
  @CanIgnoreReturnValue
  public RocketMqTelemetryBuilder setCapturedHeaders(Collection<String> capturedHeaders) {
    this.capturedHeaders = new ArrayList<>(capturedHeaders);
    return this;
  }

  /**
   * Returns a new {@link RocketMqTelemetry} with the settings of this {@link
   * RocketMqTelemetryBuilder}.
   */
  public RocketMqTelemetry build() {
    return new RocketMqTelemetry(openTelemetry, capturedHeaders, captureExperimentalSpanAttributes);
  }
}
