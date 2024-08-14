/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse;

import com.google.auto.value.AutoValue;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlStatementInfo;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlStatementSanitizer;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;

@AutoValue
public abstract class ClickHouseDbRequest {

  private static final SqlStatementSanitizer sanitizer =
      SqlStatementSanitizer.create(AgentCommonConfig.get().isStatementSanitizationEnabled());

  public static ClickHouseDbRequest create(String host, int port, String dbName, String sql) {
    return new AutoValue_ClickHouseDbRequest(host, port, dbName, sanitizer.sanitize(sql));
  }

  public abstract String getHost();

  public abstract int getPort();

  public abstract String getDbName();

  public abstract SqlStatementInfo getSqlStatementInfo();
}
