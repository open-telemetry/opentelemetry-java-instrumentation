/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jdbc;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.db.DbAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.db.DbSpanNameExtractor;

public final class JdbcInstrumenters {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.javaagent.jdbc";

  private static final Instrumenter<DbRequest, Void> INSTRUMENTER;

  static {
    DbAttributesExtractor<DbRequest> attributesExtractor = new JdbcAttributesExtractor();
    SpanNameExtractor<DbRequest> spanName = DbSpanNameExtractor.create(attributesExtractor);

    INSTRUMENTER =
        Instrumenter.<DbRequest, Void>newBuilder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, spanName)
            .addAttributesExtractor(attributesExtractor)
            .addAttributesExtractor(new JdbcNetAttributesExtractor())
            .newInstrumenter(SpanKindExtractor.alwaysClient());
  }

  public static Instrumenter<DbRequest, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private JdbcInstrumenters() {}
}
