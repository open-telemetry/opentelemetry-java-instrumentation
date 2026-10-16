/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v3_0;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbExceptionEventExtractors.setDbClientExceptionEventExtractor;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DbConfig;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;

class CassandraSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.cassandra-3.0";

  private static final Instrumenter<CassandraRequest, CassandraResponse> instrumenter;

  static {
    CassandraSqlAttributesGetter attributesGetter = new CassandraSqlAttributesGetter();

    InstrumenterBuilder<CassandraRequest, CassandraResponse> builder =
        Instrumenter.<CassandraRequest, CassandraResponse>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                DbClientSpanNameExtractor.create(attributesGetter))
            .addAttributesExtractor(
                SqlClientAttributesExtractor.builder(attributesGetter)
                    .setSingleOperationAndCollection(true)
                    .setQuerySanitizationEnabled(
                        DbConfig.isQuerySanitizationEnabled(GlobalOpenTelemetry.get(), "cassandra"))
                    .build())
            .addAttributesExtractor(new CassandraAttributesExtractor())
            .addOperationMetrics(DbClientMetrics.get());
    setDbClientExceptionEventExtractor(builder);

    instrumenter = builder.buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  static Instrumenter<CassandraRequest, CassandraResponse> instrumenter() {
    return instrumenter;
  }

  private CassandraSingletons() {}
}
