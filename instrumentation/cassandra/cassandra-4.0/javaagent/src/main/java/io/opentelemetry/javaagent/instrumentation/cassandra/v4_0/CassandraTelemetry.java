/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v4_0;

import com.datastax.oss.driver.api.core.cql.ExecutionInfo;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.db.SqlClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;

public final class CassandraTelemetry {

  public static CassandraTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new CassandraTelemetryBuilder(openTelemetry);
  }

  private final Instrumenter<CassandraRequest, ExecutionInfo> instrumenter;

  CassandraTelemetry(
      OpenTelemetry openTelemetry,
      String instrumentationName,
      boolean statementSanitizationEnabled) {
    this.instrumenter =
        createInstrumenter(openTelemetry, instrumentationName, statementSanitizationEnabled);
  }

  private static Instrumenter<CassandraRequest, ExecutionInfo> createInstrumenter(
      OpenTelemetry openTelemetry, String instrumenterName, boolean statementSanitizationEnabled) {
    CassandraSqlAttributesGetter attributesGetter = new CassandraSqlAttributesGetter();

    return Instrumenter.<CassandraRequest, ExecutionInfo>builder(
            openTelemetry, instrumenterName, DbClientSpanNameExtractor.create(attributesGetter))
        .addAttributesExtractor(
            SqlClientAttributesExtractor.builder(attributesGetter)
                .setTableAttribute(SemanticAttributes.DB_CASSANDRA_TABLE)
                .setStatementSanitizationEnabled(statementSanitizationEnabled)
                .build())
        .addAttributesExtractor(
            NetClientAttributesExtractor.create(new CassandraNetAttributesGetter()))
        .addAttributesExtractor(new CassandraAttributesExtractor())
        .buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  public Instrumenter<CassandraRequest, ExecutionInfo> getInstrumenter() {
    return instrumenter;
  }
}
