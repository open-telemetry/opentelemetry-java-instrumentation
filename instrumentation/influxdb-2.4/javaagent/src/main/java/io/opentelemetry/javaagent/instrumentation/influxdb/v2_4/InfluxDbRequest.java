/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.influxdb.v2_4;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect.DOUBLE_QUOTES_ARE_IDENTIFIERS;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldDatabaseSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;

import com.google.auto.value.AutoValue;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlQuery;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlQueryAnalyzer;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import javax.annotation.Nullable;

@AutoValue
public abstract class InfluxDbRequest {

  private static final SqlQueryAnalyzer analyzer =
      SqlQueryAnalyzer.create(AgentCommonConfig.get().isQuerySanitizationEnabled());

  public static InfluxDbRequest create(
      String host, int port, String namespace, @Nullable String operationName, @Nullable String sql) {
    // "String literals must be surrounded by single quotes."
    // https://docs.influxdata.com/influxdb/v2/reference/syntax/influxql/spec/#strings
    SqlQuery sqlQuery =
        emitOldDatabaseSemconv() ? analyzer.analyze(sql, DOUBLE_QUOTES_ARE_IDENTIFIERS) : null;
    SqlQuery sqlQueryWithSummary =
        emitStableDatabaseSemconv()
            ? analyzer.analyzeWithSummary(sql, DOUBLE_QUOTES_ARE_IDENTIFIERS)
            : null;
    return new AutoValue_InfluxDbRequest(
        host, port, namespace, operationName, sqlQuery, sqlQueryWithSummary);
  }

  public abstract String getHost();

  public abstract int getPort();

  public abstract String getNamespace();

  @Nullable
  public abstract String getOperationName();

  @Nullable
  public abstract String getSql();
}
