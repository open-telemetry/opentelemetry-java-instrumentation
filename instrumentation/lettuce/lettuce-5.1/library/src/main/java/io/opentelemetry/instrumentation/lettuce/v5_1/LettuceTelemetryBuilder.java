/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1;

import static io.opentelemetry.instrumentation.lettuce.v5_1.LettuceTelemetry.INSTRUMENTATION_NAME;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientMetrics;

/** A builder of {@link LettuceTelemetry}. */
public final class LettuceTelemetryBuilder {

  private final OpenTelemetry openTelemetry;

  private boolean statementSanitizationEnabled = true;
  private boolean encodingEventsEnabled = false;
  private boolean captureQueryParameters = false;

  LettuceTelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Sets whether the {@code db.statement} attribute on the spans emitted by the constructed {@link
   * LettuceTelemetry} should be sanitized. If set to {@code true}, all parameters that can
   * potentially contain sensitive information will be masked. Enabled by default.
   */
  @CanIgnoreReturnValue
  public LettuceTelemetryBuilder setStatementSanitizationEnabled(
      boolean statementSanitizationEnabled) {
    this.statementSanitizationEnabled = statementSanitizationEnabled;
    return this;
  }

  /**
   * Sets whether the {@code redis.encode.start} and {@code redis.encode.end} span events should be
   * emitted by the constructed {@link LettuceTelemetry}. Disabled by default.
   */
  @CanIgnoreReturnValue
  public LettuceTelemetryBuilder setEncodingSpanEventsEnabled(boolean encodingEventsEnabled) {
    this.encodingEventsEnabled = encodingEventsEnabled;
    return this;
  }

  /**
   * Sets whether query parameters should be captured as {@code db.query.parameter.*} span
   * attributes. When enabled, this will disable statement sanitization. Disabled by default.
   *
   * <p><b>WARNING:</b> Captured query parameters may contain sensitive information such as
   * passwords, personally identifiable information, or protected health information.
   */
  @CanIgnoreReturnValue
  public LettuceTelemetryBuilder setCaptureQueryParameters(boolean captureQueryParameters) {
    this.captureQueryParameters = captureQueryParameters;
    return this;
  }

  /**
   * Returns a new {@link LettuceTelemetry} with the settings of this {@link
   * LettuceTelemetryBuilder}.
   */
  public LettuceTelemetry build() {
    return new LettuceTelemetry(
        openTelemetry,
        statementSanitizationEnabled,
        encodingEventsEnabled,
        captureQueryParameters,
        DbClientMetrics.get().create(openTelemetry.getMeterProvider().get(INSTRUMENTATION_NAME)));
  }
}
