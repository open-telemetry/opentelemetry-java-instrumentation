/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.r2dbc.v1_0.internal;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.db.SqlClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.network.ServerAttributesExtractor;
import java.util.ArrayList;
import java.util.List;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class R2dbcInstrumenterBuilder {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.r2dbc-1.0";

  private final OpenTelemetry openTelemetry;

  private final List<AttributesExtractor<DbExecution, Void>> additionalExtractors =
      new ArrayList<>();

  public R2dbcInstrumenterBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  @CanIgnoreReturnValue
  public R2dbcInstrumenterBuilder addAttributeExtractor(
      AttributesExtractor<DbExecution, Void> attributesExtractor) {
    additionalExtractors.add(attributesExtractor);
    return this;
  }

  public Instrumenter<DbExecution, Void> build(boolean statementSanitizationEnabled) {

    return Instrumenter.<DbExecution, Void>builder(
            openTelemetry,
            INSTRUMENTATION_NAME,
            DbClientSpanNameExtractor.create(R2dbcSqlAttributesGetter.INSTANCE))
        .addAttributesExtractor(
            SqlClientAttributesExtractor.builder(R2dbcSqlAttributesGetter.INSTANCE)
                .setStatementSanitizationEnabled(statementSanitizationEnabled)
                .build())
        .addAttributesExtractor(ServerAttributesExtractor.create(R2dbcNetAttributesGetter.INSTANCE))
        .buildInstrumenter(SpanKindExtractor.alwaysClient());
  }
}
