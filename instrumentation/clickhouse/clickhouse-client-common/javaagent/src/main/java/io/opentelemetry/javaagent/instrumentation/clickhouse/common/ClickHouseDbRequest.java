/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse.common;

import com.google.auto.value.AutoValue;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlQuery;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlQuerySanitizer;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import javax.annotation.Nullable;

@AutoValue
public abstract class ClickHouseDbRequest {

  private static final SqlQuerySanitizer sanitizer =
      SqlQuerySanitizer.create(AgentCommonConfig.get().isQuerySanitizationEnabled());

  public static ClickHouseDbRequest create(
      @Nullable String host, @Nullable Integer port, @Nullable String namespace, String sql) {
    SqlQuery sqlQuery = SemconvStability.emitOldDatabaseSemconv() ? sanitizer.sanitize(sql) : null;
    SqlQuery sqlQueryWithSummary =
        SemconvStability.emitStableDatabaseSemconv() ? sanitizer.sanitizeWithSummary(sql) : null;
    return new AutoValue_ClickHouseDbRequest(host, port, namespace, sqlQuery, sqlQueryWithSummary);
  }

  @Nullable
  public abstract String getHost();

  @Nullable
  public abstract Integer getPort();

  @Nullable
  public abstract String getNamespace();

  @Nullable
  public abstract SqlQuery getSqlQuery();

  @Nullable
  public abstract SqlQuery getSqlQueryWithSummary();
}
