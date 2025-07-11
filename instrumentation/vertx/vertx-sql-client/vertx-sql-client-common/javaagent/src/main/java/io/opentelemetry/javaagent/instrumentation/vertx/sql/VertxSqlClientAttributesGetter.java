/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sql;

import static java.util.Collections.singleton;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlClientAttributesGetter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nullable;

enum VertxSqlClientAttributesGetter
    implements SqlClientAttributesGetter<VertxSqlClientRequest, Void> {
  INSTANCE;

  private static final List<Function<Exception, String>> responseStatusExtractors =
      createResponseStatusExtractors();

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
    for (Function<Exception, String> extractor : responseStatusExtractors) {
      String status = extractor.apply((Exception) error);
      if (status != null) {
        return status;
      }
    }
    return null;
  }

  private static List<Function<Exception, String>> createResponseStatusExtractors() {
    return Arrays.asList(
        responseStatusExtractor("io.vertx.sqlclient.DatabaseException", "getSqlState"),
        // older version only have this method
        responseStatusExtractor("io.vertx.pgclient.PgException", "getCode"));
  }

  private static Function<Exception, String> responseStatusExtractor(
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
