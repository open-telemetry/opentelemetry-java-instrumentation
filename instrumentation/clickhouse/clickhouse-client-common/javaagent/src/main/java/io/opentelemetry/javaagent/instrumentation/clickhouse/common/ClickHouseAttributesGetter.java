/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse.common;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import java.util.function.Function;
import javax.annotation.Nullable;

final class ClickHouseAttributesGetter
    implements DbClientAttributesGetter<ClickHouseDbRequest, Void> {

  private final Function<Throwable, String> errorCodeExtractor;

  ClickHouseAttributesGetter(Function<Throwable, String> errorCodeExtractor) {
    this.errorCodeExtractor = errorCodeExtractor;
  }

  @Nullable
  @Override
  public String getDbQueryText(ClickHouseDbRequest request) {
    if (request.getSqlStatementInfo() == null) {
      return null;
    }
    return request.getSqlStatementInfo().getFullStatement();
  }

  @Nullable
  @Override
  public String getDbOperationName(ClickHouseDbRequest request) {
    if (request.getSqlStatementInfo() == null) {
      return null;
    }
    return request.getSqlStatementInfo().getOperation();
  }

  @SuppressWarnings("deprecation") // using deprecated DbSystemIncubatingValues
  @Override
  public String getDbSystemName(ClickHouseDbRequest request) {
    return DbIncubatingAttributes.DbSystemIncubatingValues.CLICKHOUSE;
  }

  @Nullable
  @Override
  public String getDbNamespace(ClickHouseDbRequest request) {
    String dbName = request.getDbName();
    if (dbName == null || dbName.isEmpty()) {
      return null;
    }
    return dbName;
  }

  @Nullable
  @Override
  public String getResponseStatusCode(@Nullable Void response, @Nullable Throwable error) {
    return errorCodeExtractor.apply(error);
  }
}
