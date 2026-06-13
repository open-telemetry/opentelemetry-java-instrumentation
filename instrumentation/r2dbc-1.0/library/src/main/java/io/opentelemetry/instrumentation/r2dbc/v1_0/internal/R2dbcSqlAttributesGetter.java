/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.r2dbc.v1_0.internal;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect.DOUBLE_QUOTES_ARE_STRING_LITERALS;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static java.util.Collections.singleton;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlClientAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect;
import io.r2dbc.spi.R2dbcException;
import java.util.Collection;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class R2dbcSqlAttributesGetter
    implements SqlClientAttributesGetter<DbExecution, Void> {

  @Override
  public String getDbSystemName(DbExecution request) {
    return request.getSystemName();
  }

  @Deprecated // to be removed in 3.0
  @Override
  public String getDbSystem(DbExecution request) {
    return request.getSystem();
  }

  @Override
  public SqlDialect getSqlDialect(DbExecution request) {
    // the underlying database is unknown, use the safer default that sanitizes double-quoted
    // fragments as string literals (note that this can lead to incorrect summarization
    // for databases that do use double quotes as identifiers)
    //
    // TODO do better in
    // https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/16251
    return DOUBLE_QUOTES_ARE_STRING_LITERALS;
  }

  @Deprecated // to be removed in 3.0
  @Override
  @Nullable
  public String getUser(DbExecution request) {
    return request.getUser();
  }

  @Override
  @Nullable
  public String getDbNamespace(DbExecution request) {
    return request.getNamespace();
  }

  @Deprecated // to be removed in 3.0
  @Override
  public String getConnectionString(DbExecution request) {
    return request.getConnectionString();
  }

  @Override
  public Collection<String> getRawQueryTexts(DbExecution request) {
    Collection<String> rawQueryTexts = request.getRawQueryTexts();
    // In old-only mode, join multi-query batches into a single query to preserve the legacy
    // db.statement and db.operation extraction behavior. In database/dup mode, favor stable
    // multi-query batch attributes because the shared SQL extractor can only use one raw query
    // collection.
    return emitStableDatabaseSemconv() || rawQueryTexts.size() == 1
        ? rawQueryTexts
        : singleton(join("; ", rawQueryTexts));
  }

  private static String join(String delimiter, Collection<String> collection) {
    StringBuilder builder = new StringBuilder();
    for (String string : collection) {
      if (builder.length() != 0) {
        builder.append(delimiter);
      }
      builder.append(string);
    }
    return builder.toString();
  }

  @Override
  @Nullable
  public Long getDbOperationBatchSize(DbExecution request) {
    // Batch size is a stable database semconv signal. Keep it hidden from old-only mode so legacy
    // extraction does not start treating existing requests as batches.
    return emitStableDatabaseSemconv() ? request.getBatchSize() : null;
  }

  @Nullable
  @Override
  public String getErrorType(
      DbExecution request, @Nullable Void response, @Nullable Throwable error) {
    if (error instanceof R2dbcException) {
      return ((R2dbcException) error).getSqlState();
    }
    return null;
  }

  @Nullable
  @Override
  public String getServerAddress(DbExecution request) {
    return request.getServerAddress();
  }

  @Nullable
  @Override
  public Integer getServerPort(DbExecution request) {
    return request.getServerPort();
  }

  @Override
  public boolean isParameterizedQuery(DbExecution request, int queryIndex) {
    // R2DBC does not support mixed parameterization within a single request.
    return request.isParameterizedQuery();
  }
}
