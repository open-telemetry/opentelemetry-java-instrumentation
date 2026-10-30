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

/** A builder of {@link RocketMqTelemetry}. */
public final class RocketMqTelemetryBuilder {

  private final OpenTelemetry openTelemetry;

  private Collection<String> headers = emptyList();
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
   * Configures which message headers are captured as span attributes.
   *
   * <p>Header values are captured under the {@code messaging.header.<name>} attribute key. The
   * {@code <name>} part in the attribute key is the header name with its original spelling,
   * including dashes.
   *
   * <p>Names are looked up literally using the messaging library's header-name case sensitivity.
   * {@code *} and {@code ?} are not wildcards. No headers are captured by default.
   */
  @CanIgnoreReturnValue
  public RocketMqTelemetryBuilder setCapturedHeaders(Collection<String> capturedHeaders) {
    this.headers = new ArrayList<>(capturedHeaders);
    return this;
  }

  /**
   * Returns a new {@link RocketMqTelemetry} with the settings of this {@link
   * RocketMqTelemetryBuilder}.
   */
  public RocketMqTelemetry build() {
    return new RocketMqTelemetry(openTelemetry, headers, captureExperimentalSpanAttributes);
  }
}
