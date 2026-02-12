/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1;

import static io.opentelemetry.instrumentation.lettuce.v5_1.LettuceTelemetry.INSTRUMENTATION_NAME;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;

/** A builder of {@link LettuceTelemetry}. */
public final class LettuceTelemetryBuilder {

  private final OpenTelemetry openTelemetry;

  private boolean querySanitizationEnabled = true;
  private boolean encodingEventsEnabled = false;

  LettuceTelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Sets whether the {@code db.statement}/{@code db.query.text} attribute on the spans emitted by
   * the constructed {@link LettuceTelemetry} should be sanitized. If set to {@code true}, all
   * parameters that can potentially contain sensitive information will be masked. Enabled by
   * default.
   */
  @CanIgnoreReturnValue
  public LettuceTelemetryBuilder setQuerySanitizationEnabled(boolean querySanitizationEnabled) {
    this.querySanitizationEnabled = querySanitizationEnabled;
    return this;
  }

  /**
   * @deprecated Use {@link #setQuerySanitizationEnabled(boolean)} instead.
   */
  @Deprecated
  @CanIgnoreReturnValue
  public LettuceTelemetryBuilder setStatementSanitizationEnabled(
      boolean statementSanitizationEnabled) {
    return setQuerySanitizationEnabled(statementSanitizationEnabled);
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

    Instrumenter<LettuceRequest, LettuceResponse> instrumenter =
        Instrumenter.<LettuceRequest, LettuceResponse>builder(
                openTelemetry,
                INSTRUMENTATION_NAME,
                DbClientSpanNameExtractor.create(dbAttributesGetter))
            .addAttributesExtractor(DbClientAttributesExtractor.create(dbAttributesGetter))
            .addOperationMetrics(DbClientMetrics.get())
            .setSpanStatusExtractor(
                (spanStatusBuilder, request, response, error) -> {
                  if (response != null && response.getErrorMessage() != null) {
                    spanStatusBuilder.setStatus(StatusCode.ERROR);
                  } else {
                    SpanStatusExtractor.<LettuceRequest, LettuceResponse>getDefault()
                        .extract(spanStatusBuilder, request, response, error);
                  }
                })
            .buildInstrumenter(SpanKindExtractor.alwaysClient());

    return new LettuceTelemetry(instrumenter, querySanitizationEnabled, encodingEventsEnabled);
  }
}
