/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1;

import static io.opentelemetry.instrumentation.lettuce.v5_1.LettuceTelemetry.INSTRUMENTATION_NAME;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;

/** A builder of {@link LettuceTelemetry}. */
public final class LettuceTelemetryBuilder {

  private final OpenTelemetry openTelemetry;

  private boolean statementSanitizationEnabled = true;
  private boolean encodingEventsEnabled = false;

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
   * Returns a new {@link LettuceTelemetry} with the settings of this {@link
   * LettuceTelemetryBuilder}.
   */
  public LettuceTelemetry build() {
    LettuceDbAttributesGetter dbAttributesGetter = new LettuceDbAttributesGetter();

    Instrumenter<LettuceRequest, Void> instrumenter =
        Instrumenter.<LettuceRequest, Void>builder(
                openTelemetry,
                INSTRUMENTATION_NAME,
                DbClientSpanNameExtractor.create(dbAttributesGetter))
            .addAttributesExtractor(DbClientAttributesExtractor.create(dbAttributesGetter))
            .addOperationMetrics(DbClientMetrics.get())
            .buildInstrumenter(SpanKindExtractor.alwaysClient());

    return new LettuceTelemetry(instrumenter, statementSanitizationEnabled, encodingEventsEnabled);
  }
}
