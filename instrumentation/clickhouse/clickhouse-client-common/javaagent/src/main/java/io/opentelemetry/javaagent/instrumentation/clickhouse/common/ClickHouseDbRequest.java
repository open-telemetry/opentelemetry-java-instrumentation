/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse.common;

import com.google.auto.value.AutoValue;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlStatementInfo;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlStatementSanitizer;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import javax.annotation.Nullable;

@AutoValue
public abstract class ClickHouseDbRequest {

  private static final SqlStatementSanitizer sanitizer =
      SqlStatementSanitizer.create(AgentCommonConfig.get().isStatementSanitizationEnabled());

  public static ClickHouseDbRequest create(
      @Nullable String host, @Nullable Integer port, @Nullable String dbName, String sql) {
    SqlStatementInfo sqlStatementInfo =
        SemconvStability.emitOldDatabaseSemconv() ? sanitizer.sanitize(sql) : null;
    SqlStatementInfo sqlStatementInfoWithSummary =
        SemconvStability.emitStableDatabaseSemconv() ? sanitizer.sanitizeWithSummary(sql) : null;
    return new AutoValue_ClickHouseDbRequest(
        host, port, dbName, sqlStatementInfo, sqlStatementInfoWithSummary);
  }

  @Nullable
  public abstract String getHost();

  @Nullable
  public abstract Integer getPort();

  @Nullable
  public abstract String getDbName();

  @Nullable
  public abstract SqlStatementInfo getSqlStatementInfo();

  @Nullable
  public abstract SqlStatementInfo getSqlStatementInfoWithSummary();
}
