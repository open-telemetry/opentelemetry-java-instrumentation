/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbExceptionEventExtractors.setDbClientExceptionEventExtractor;
import static io.opentelemetry.instrumentation.lettuce.v5_1.LettuceTelemetry.INSTRUMENTATION_NAME;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbSchemaUrls;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;

/** A builder of {@link LettuceTelemetry}. */
public final class LettuceTelemetryBuilder {

  private final OpenTelemetry openTelemetry;

  private boolean querySanitizationEnabled = true;

  LettuceTelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Sets whether the {@code db.query.text} attribute on the spans emitted by the constructed {@link
   * LettuceTelemetry} should be sanitized. If set to {@code true}, all parameters that can
   * potentially contain sensitive information will be masked. Enabled by default.
   */
  @CanIgnoreReturnValue
  public LettuceTelemetryBuilder setQuerySanitizationEnabled(boolean querySanitizationEnabled) {
    this.querySanitizationEnabled = querySanitizationEnabled;
    return this;
  }

  /**
   * Returns a new {@link LettuceTelemetry} with the settings of this {@link
   * LettuceTelemetryBuilder}.
   */
  public LettuceTelemetry build() {
    LettuceDbAttributesGetter dbAttributesGetter = new LettuceDbAttributesGetter();

    InstrumenterBuilder<LettuceRequest, LettuceResponse> builder =
        Instrumenter.<LettuceRequest, LettuceResponse>builder(
                openTelemetry,
                INSTRUMENTATION_NAME,
                RedisSpanNameExtractor.create(dbAttributesGetter))
            .addAttributesExtractor(DbClientAttributesExtractor.create(dbAttributesGetter))
            .setSchemaUrl(DbSchemaUrls.SCHEMA_URL)
            .addOperationMetrics(DbClientMetrics.get())
            .setSpanStatusExtractor(
                (spanStatusBuilder, request, response, error) -> {
                  if (response != null && response.isFailed()) {
                    spanStatusBuilder.setStatus(StatusCode.ERROR);
                  } else {
                    SpanStatusExtractor.getDefault()
                        .extract(spanStatusBuilder, request, response, error);
                  }
                });
    setDbClientExceptionEventExtractor(builder);

    Instrumenter<LettuceRequest, LettuceResponse> instrumenter =
        builder.buildInstrumenter(SpanKindExtractor.alwaysClient());

    return new LettuceTelemetry(instrumenter, querySanitizationEnabled);
  }
}
