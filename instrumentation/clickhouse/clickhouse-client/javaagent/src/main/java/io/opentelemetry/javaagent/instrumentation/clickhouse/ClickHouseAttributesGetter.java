/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse;

import io.opentelemetry.api.internal.StringUtils;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import javax.annotation.Nullable;

final class ClickHouseAttributesGetter implements DbClientAttributesGetter<ClickHouseDbRequest> {
  @Nullable
  @Override
  public String getStatement(ClickHouseDbRequest request) {
    if (request.getSqlStatementInfo() == null) {
      return null;
    }
    String statement = request.getSqlStatementInfo().getFullStatement();
    return StringUtils.isNullOrEmpty(statement) ? null : statement;
  }

  @Nullable
  @Override
  public String getOperation(ClickHouseDbRequest request) {
    if (request.getSqlStatementInfo() == null) {
      return null;
    }
    String operation = request.getSqlStatementInfo().getOperation();
    return StringUtils.isNullOrEmpty(operation) ? null : operation;
  }

  @Nullable
  @Override
  public String getSystem(ClickHouseDbRequest request) {
    return DbIncubatingAttributes.DbSystemValues.CLICKHOUSE;
  }

  @Nullable
  @Override
  public String getUser(ClickHouseDbRequest request) {
    return null;
  }

  @Nullable
  @Override
  public String getName(ClickHouseDbRequest request) {
    String dbName = request.getDbName();
    return StringUtils.isNullOrEmpty(dbName) ? null : dbName;
  }

  @Nullable
  @Override
  public String getConnectionString(ClickHouseDbRequest request) {
    return null;
  }
}
