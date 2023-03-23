/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.r2dbc.v1_0;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.javaagent.instrumentation.r2dbc.v1_0.internal.DbExecution;
import io.opentelemetry.javaagent.instrumentation.r2dbc.v1_0.internal.R2dbcInstrumenterBuilder;

/** A builder of {@link R2dbcTelemetry}. */
public final class R2dbcTelemetryBuilder {

  private final R2dbcInstrumenterBuilder instrumenterBuilder;
  private boolean statementSanitizationEnabled = true;

  R2dbcTelemetryBuilder(OpenTelemetry openTelemetry) {
    instrumenterBuilder = new R2dbcInstrumenterBuilder(openTelemetry);
  }

  @CanIgnoreReturnValue
  public R2dbcTelemetryBuilder addAttributeExtractor(
      AttributesExtractor<DbExecution, Void> attributesExtractor) {
    instrumenterBuilder.addAttributeExtractor(attributesExtractor);
    return this;
  }

  /**
   * Sets whether the {@code db.statement} attribute on the spans emitted by the constructed {@link
   * R2dbcTelemetry} should be sanitized. If set to {@code true}, all parameters that can
   * potentially contain sensitive information will be masked. Enabled by default.
   */
  @CanIgnoreReturnValue
  public R2dbcTelemetryBuilder setStatementSanitizationEnabled(boolean enabled) {
    this.statementSanitizationEnabled = enabled;
    return this;
  }

  /**
   * Returns a new {@link R2dbcTelemetry} with the settings of this {@link R2dbcTelemetryBuilder}.
   */
  public R2dbcTelemetry build() {
    return new R2dbcTelemetry(instrumenterBuilder.build(statementSanitizationEnabled));
  }
}
