/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.r2dbc.v1_0.internal;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect.DOUBLE_QUOTES_ARE_STRING_LITERALS;
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
public enum R2dbcSqlAttributesGetter implements SqlClientAttributesGetter<DbExecution, Void> {
  INSTANCE;

  @Override
  public String getDbSystemName(DbExecution request) {
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
    return request.getName();
  }

  @Deprecated // to be removed in 3.0
  @Override
  @Nullable
  public String getConnectionString(DbExecution request) {
    return request.getConnectionString();
  }

  @Override
  public Collection<String> getRawQueryTexts(DbExecution request) {
    return singleton(request.getRawQueryText());
  }

  @Nullable
  @Override
  public String getDbResponseStatusCode(@Nullable Void response, @Nullable Throwable error) {
    if (error instanceof R2dbcException) {
      return ((R2dbcException) error).getSqlState();
    }
    return null;
  }

  @Nullable
  @Override
  public String getServerAddress(DbExecution request) {
    return request.getHost();
  }

  @Nullable
  @Override
  public Integer getServerPort(DbExecution request) {
    return request.getPort();
  }

  @Override
  public boolean isParameterizedQuery(DbExecution request) {
    return request.isParameterizedQuery();
  }
}
