/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse.common;

import com.google.auto.value.AutoValue;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlStatementInfo;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlStatementSanitizer;
import javax.annotation.Nullable;

@AutoValue
public abstract class ClickHouseDbRequest {

  private static final SqlStatementSanitizer sanitizer =
      SqlStatementSanitizer.create(
          DeclarativeConfigUtil.getBoolean(
                  GlobalOpenTelemetry.get(), "general", "db", "statement_sanitizer", "enabled")
              .orElse(true));

  public static ClickHouseDbRequest create(
      @Nullable String host, @Nullable Integer port, @Nullable String dbName, String sql) {
    return new AutoValue_ClickHouseDbRequest(host, port, dbName, sanitizer.sanitize(sql));
  }

  @Nullable
  public abstract String getHost();

  @Nullable
  public abstract Integer getPort();

  @Nullable
  public abstract String getDbName();

  public abstract SqlStatementInfo getSqlStatementInfo();
}
