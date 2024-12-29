/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.cassandra.v4_4;

import com.datastax.oss.driver.api.core.cql.ExecutionInfo;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesExtractor;

/** A builder of {@link CassandraTelemetry}. */
public class CassandraTelemetryBuilder {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.cassandra-4.4";
  // copied from DbIncubatingAttributes
  private static final AttributeKey<String> DB_CASSANDRA_TABLE =
      AttributeKey.stringKey("db.cassandra.table");

  private final OpenTelemetry openTelemetry;

  private boolean statementSanitizationEnabled = true;

  protected CassandraTelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Sets whether the {@code db.statement} attribute on the spans emitted by the constructed {@link
   * CassandraTelemetry} should be sanitized. If set to {@code true}, all parameters that can
   * potentially contain sensitive information will be masked. Enabled by default.
   */
  @CanIgnoreReturnValue
  public CassandraTelemetryBuilder setStatementSanitizationEnabled(boolean enabled) {
    this.statementSanitizationEnabled = enabled;
    return this;
  }

  /**
   * Returns a new {@link CassandraTelemetry} with the settings of this {@link
   * CassandraTelemetryBuilder}.
   */
  public CassandraTelemetry build() {
    return new CassandraTelemetry(createInstrumenter(openTelemetry, statementSanitizationEnabled));
  }

  @SuppressWarnings("deprecation") // to support old database semantic conventions
  protected Instrumenter<CassandraRequest, ExecutionInfo> createInstrumenter(
      OpenTelemetry openTelemetry, boolean statementSanitizationEnabled) {
    CassandraSqlAttributesGetter attributesGetter = new CassandraSqlAttributesGetter();

    return Instrumenter.<CassandraRequest, ExecutionInfo>builder(
            openTelemetry, INSTRUMENTATION_NAME, DbClientSpanNameExtractor.create(attributesGetter))
        .addAttributesExtractor(
            SqlClientAttributesExtractor.builder(attributesGetter)
                .setTableAttribute(DB_CASSANDRA_TABLE)
                .setStatementSanitizationEnabled(statementSanitizationEnabled)
                .build())
        .addAttributesExtractor(
            NetworkAttributesExtractor.create(new CassandraNetworkAttributesGetter()))
        .addAttributesExtractor(new CassandraAttributesExtractor())
        .addOperationMetrics(DbClientMetrics.get())
        .buildInstrumenter(SpanKindExtractor.alwaysClient());
  }
}
