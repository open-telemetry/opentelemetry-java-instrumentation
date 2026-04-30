/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v4_0;

import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_CASSANDRA_TABLE;

import com.datastax.oss.driver.api.core.cql.ExecutionInfo;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DbConfig;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;

@SuppressWarnings("deprecation") // using deprecated semconv
class CassandraSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.cassandra-4.0";

  // using ExecutionInfo because we can get that from ResultSet, AsyncResultSet and DriverException
  private static final Instrumenter<CassandraRequest, ExecutionInfo> instrumenter;

  static {
    CassandraSqlAttributesGetter attributesGetter = new CassandraSqlAttributesGetter();

    instrumenter =
        Instrumenter.<CassandraRequest, ExecutionInfo>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                DbClientSpanNameExtractor.create(attributesGetter))
            .addAttributesExtractor(
                SqlClientAttributesExtractor.builder(attributesGetter)
                    .setTableAttribute(DB_CASSANDRA_TABLE)
                    .setQuerySanitizationEnabled(
                        DbConfig.isQuerySanitizationEnabled(GlobalOpenTelemetry.get(), "cassandra"))
                    .build())
            .addAttributesExtractor(new CassandraAttributesExtractor())
            .addOperationMetrics(DbClientMetrics.get())
            .buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  static Instrumenter<CassandraRequest, ExecutionInfo> instrumenter() {
    return instrumenter;
  }

  private CassandraSingletons() {}
}
