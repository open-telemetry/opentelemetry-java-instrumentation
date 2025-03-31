/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v4_0.sql;

import static java.util.Collections.singleton;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlClientAttributesGetter;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import javax.annotation.Nullable;

public enum VertxSqlClientAttributesGetter
    implements SqlClientAttributesGetter<VertxSqlClientRequest, Void> {
  INSTANCE;

  @Override
  public String getDbSystem(VertxSqlClientRequest request) {
    return null;
  }

  @Deprecated
  @Override
  @Nullable
  public String getUser(VertxSqlClientRequest request) {
    return request.getUser();
  }

  @Override
  @Nullable
  public String getDbNamespace(VertxSqlClientRequest request) {
    return request.getDatabase();
  }

  @Deprecated
  @Override
  @Nullable
  public String getConnectionString(VertxSqlClientRequest request) {
    return null;
  }

  @Override
  public Collection<String> getRawQueryTexts(VertxSqlClientRequest request) {
    return singleton(request.getQueryText());
  }

  @Nullable
  @Override
  public String getResponseStatus(@Nullable Void response, @Nullable Throwable error) {
    try {
      // loaded via reflection, because this class is not available in all versions that we support
      Class<?> ex = Class.forName("io.vertx.pgclient.PgException");
      if (ex.isInstance(error)) {
        return (String) ex.getMethod("getCode").invoke(error);
      }
    } catch (ClassNotFoundException
        | NoSuchMethodException
        | IllegalAccessException
        | InvocationTargetException e) {
      return null;
    }
    return null;
  }
}
