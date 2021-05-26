/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v3_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.db.DbAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.db.DbSpanNameExtractor;

public final class CassandraInstrumenters {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.javaagent.cassandra-3.0";

  private static final Instrumenter<CassandraRequest, Void> INSTRUMENTER;

  static {
    DbAttributesExtractor<CassandraRequest> attributesExtractor =
        new CassandraSqlAttributesExtractor();
    SpanNameExtractor<CassandraRequest> spanName = DbSpanNameExtractor.create(attributesExtractor);

    INSTRUMENTER =
        Instrumenter.<CassandraRequest, Void>newBuilder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, spanName)
            .addAttributesExtractor(attributesExtractor)
            .addAttributesExtractor(new CassandraNetAttributesExtractor())
            .addAttributesExtractor(new CassandraKeyspaceExtractor())
            .newInstrumenter(SpanKindExtractor.alwaysClient());
  }

  public static Instrumenter<CassandraRequest, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private CassandraInstrumenters() {}
}
