/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1;

import io.opentelemetry.api.OpenTelemetry;

/** A builder of {@link LettuceTelemetry}. */
public final class LettuceTelemetryBuilder {

  private final OpenTelemetry openTelemetry;

  private boolean statementSanitizationEnabled = true;

  LettuceTelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Sets whether the {@code db.statement} attribute on the spans emitted by the constructed {@link
   * LettuceTelemetry} should be sanitized. If set to {@code true}, all parameters that can
   * potentially contain sensitive information will be masked. Enabled by default.
   */
  public LettuceTelemetryBuilder setStatementSanitizationEnabled(
      boolean statementSanitizationEnabled) {
    this.statementSanitizationEnabled = statementSanitizationEnabled;
    return this;
  }

  /**
   * Returns a new {@link LettuceTelemetry} with the settings of this {@link
   * LettuceTelemetryBuilder}.
   */
  public LettuceTelemetry build() {
    return new LettuceTelemetry(openTelemetry, statementSanitizationEnabled);
  }
}
