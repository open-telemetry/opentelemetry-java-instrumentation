/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.influxdb.v2_4;

import com.google.auto.value.AutoValue;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlStatementInfo;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlStatementSanitizer;
import io.opentelemetry.javaagent.bootstrap.internal.CommonConfig;

@AutoValue
public abstract class InfluxDbRequest {

  private static final SqlStatementSanitizer sanitizer =
      SqlStatementSanitizer.create(CommonConfig.get().isStatementSanitizationEnabled());

  public static InfluxDbRequest create(String host, Integer port, String dbName, String sql) {
    return new AutoValue_InfluxDbRequest(host, port, dbName, sql, sanitizer.sanitize(sql));
  }

  public abstract String getHost();

  public abstract Integer getPort();

  public abstract String getDbName();

  public abstract String getSql();

  public abstract SqlStatementInfo getSqlStatementInfo();
}
