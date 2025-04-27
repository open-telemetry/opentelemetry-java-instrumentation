/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import static java.util.Collections.emptySet;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlClientAttributesGetter;
import io.opentelemetry.instrumentation.jdbc.internal.dbinfo.DbInfo;
import java.sql.SQLException;
import java.util.Collection;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class JdbcTransactionAttributesGetter
    implements SqlClientAttributesGetter<TransactionRequest, Void> {

  @Nullable
  @Override
  public String getDbSystem(TransactionRequest request) {
    return request.getDbInfo().getSystem();
  }

  @Deprecated
  @Nullable
  @Override
  public String getUser(TransactionRequest request) {
    return request.getDbInfo().getUser();
  }

  @Nullable
  @Override
  public String getDbNamespace(TransactionRequest request) {
    DbInfo dbInfo = request.getDbInfo();
    return dbInfo.getName() == null ? dbInfo.getDb() : dbInfo.getName();
  }

  @Deprecated
  @Nullable
  @Override
  public String getConnectionString(TransactionRequest request) {
    return request.getDbInfo().getShortUrl();
  }

  @Override
  public Collection<String> getRawQueryTexts(TransactionRequest request) {
    return emptySet();
  }

  @Nullable
  @Override
  public Long getBatchSize(TransactionRequest request) {
    return null;
  }

  @Nullable
  @Override
  public String getResponseStatus(@Nullable Void response, @Nullable Throwable error) {
    if (error instanceof SQLException) {
      return Integer.toString(((SQLException) error).getErrorCode());
    }
    return null;
  }
}
