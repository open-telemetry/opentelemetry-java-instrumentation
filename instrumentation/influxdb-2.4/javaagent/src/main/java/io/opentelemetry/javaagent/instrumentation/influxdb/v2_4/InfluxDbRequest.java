/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.influxdb.v2_4;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect.INFLUXDB;

import com.google.auto.value.AutoValue;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlQuery;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlQuerySanitizer;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import javax.annotation.Nullable;

@AutoValue
public abstract class InfluxDbRequest {

  private static final SqlQuerySanitizer sanitizer =
      SqlQuerySanitizer.create(AgentCommonConfig.get().isQuerySanitizationEnabled());

  public static InfluxDbRequest create(
      String host, int port, String namespace, String operationName, String sql) {
    SqlQuery sqlQuery =
        SemconvStability.emitOldDatabaseSemconv() ? sanitizer.sanitize(sql, INFLUXDB) : null;
    SqlQuery sqlQueryWithSummary =
        SemconvStability.emitStableDatabaseSemconv()
            ? sanitizer.sanitizeWithSummary(sql, INFLUXDB)
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
  public abstract SqlQuery getSqlQuery();

  @Nullable
  public abstract SqlQuery getSqlQueryWithSummary();
}
