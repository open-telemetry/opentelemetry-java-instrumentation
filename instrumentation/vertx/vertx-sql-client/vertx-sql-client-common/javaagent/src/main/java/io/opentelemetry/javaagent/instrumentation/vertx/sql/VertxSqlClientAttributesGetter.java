/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sql;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect.DOUBLE_QUOTES_ARE_STRING_LITERALS;
import static java.util.Collections.singleton;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlClientAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.function.Function;
import javax.annotation.Nullable;

enum VertxSqlClientAttributesGetter
    implements SqlClientAttributesGetter<VertxSqlClientRequest, Void> {
  INSTANCE;

  private static final Function<Throwable, String> responseStatusExtractor =
      createResponseStatusExtractor();

  @Override
  public String getDbSystemName(VertxSqlClientRequest request) {
    return null;
  }

  @Override
  public SqlDialect getSqlDialect(VertxSqlClientRequest request) {
    // the underlying database is unknown, use the safer default that sanitizes double-quoted
    // fragments as string literals (note that this can lead to incorrect summarization
    // for databases that do use double quotes as identifiers)
    //
    // TODO do better in
    // https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/16254
    return DOUBLE_QUOTES_ARE_STRING_LITERALS;
  }

  @Deprecated // to be removed in 3.0
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

  @Nullable
  @Override
  public String getServerAddress(VertxSqlClientRequest request) {
    return request.getHost();
  }

  @Nullable
  @Override
  public Integer getServerPort(VertxSqlClientRequest request) {
    return request.getPort();
  }

  @Override
  public Collection<String> getRawQueryTexts(VertxSqlClientRequest request) {
    return singleton(request.getQueryText());
  }

  @Nullable
  @Override
  public String getErrorType(
      VertxSqlClientRequest request, @Nullable Void response, @Nullable Throwable error) {
    return responseStatusExtractor.apply(error);
  }

  @Override
  public boolean isParameterizedQuery(VertxSqlClientRequest request) {
    return request.isParameterizedQuery();
  }

  private static Function<Throwable, String> createResponseStatusExtractor() {
    Function<Throwable, String> extractor =
        responseStatusExtractor("io.vertx.sqlclient.DatabaseException", "getSqlState");
    // older versions only have this method
    Function<Throwable, String> fallback =
        responseStatusExtractor("io.vertx.pgclient.PgException", "getCode");
    return error -> {
      String status = extractor.apply(error);
      return status != null ? status : fallback.apply(error);
    };
  }

  private static Function<Throwable, String> responseStatusExtractor(
      String className, String methodName) {
    try {
      // loaded via reflection, because this class is not available in all versions that we support
      Class<?> exClass = Class.forName(className);
      Method method = exClass.getDeclaredMethod(methodName);

      return (error) -> {
        if (exClass.isInstance(error)) {
          try {
            return String.valueOf(method.invoke(error)); // can be String or int
          } catch (IllegalAccessException | InvocationTargetException e) {
            return null;
          }
        }
        return null;
      };
    } catch (ClassNotFoundException | NoSuchMethodException e) {
      return (error) -> null;
    }
  }
}
