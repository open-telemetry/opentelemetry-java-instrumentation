/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.SqlDialectUtil.fromDbSystemName;
import static java.util.Collections.singleton;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlClientAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect;
import io.opentelemetry.instrumentation.api.internal.CauseUnwrapper;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ConnectException;
import java.util.Collection;
import java.util.function.Function;
import javax.annotation.Nullable;

class VertxSqlClientAttributesGetter
    implements SqlClientAttributesGetter<VertxSqlClientRequest, Void> {

  private static final Function<Throwable, String> responseStatusExtractor =
      createResponseStatusExtractor(false);
  private static final Function<Throwable, String> dbResponseStatusExtractor =
      createResponseStatusExtractor(true);

  @Override
  public String getDbSystemName(VertxSqlClientRequest request) {
    return request.getDbSystemName();
  }

  @Override
  public SqlDialect getSqlDialect(VertxSqlClientRequest request) {
    return fromDbSystemName(request.getDbSystemName());
  }

  @Override
  @Nullable
  public String getDbNamespace(VertxSqlClientRequest request) {
    return request.getDatabase();
  }

  @Nullable
  @Override
  public String getServerAddress(VertxSqlClientRequest request) {
    return request.getConfiguredServerAddress();
  }

  @Nullable
  @Override
  public Integer getServerPort(VertxSqlClientRequest request) {
    return request.getConfiguredServerPort();
  }

  @Override
  public Collection<String> getRawQueryTexts(VertxSqlClientRequest request) {
    return singleton(request.getQueryText());
  }

  @Nullable
  @Override
  public Long getDbOperationBatchSize(VertxSqlClientRequest request) {
    return request.getOperationBatchSize();
  }

  @Nullable
  @Override
  public String getErrorType(
      VertxSqlClientRequest request, @Nullable Void response, @Nullable Throwable error) {
    return responseStatusExtractor.apply(error);
  }

  @Nullable
  @Override
  public String getDbResponseStatusCode(
      VertxSqlClientRequest request, @Nullable Void response, @Nullable Throwable error) {
    if (error != null
        && "oracle.db".equals(request.getDbSystemName())
        && CauseUnwrapper.unwrap(error, cause -> !(cause instanceof ConnectException))
            instanceof ConnectException) {
      return null;
    }
    return dbResponseStatusExtractor.apply(error);
  }

  @Override
  public boolean isParameterizedQuery(VertxSqlClientRequest request, int queryIndex) {
    // Vert.x SQL client does not support mixed parameterization within a single request.
    return request.isParameterizedQuery();
  }

  private static Function<Throwable, String> createResponseStatusExtractor(boolean preserveNull) {
    Function<Throwable, String> extractor =
        responseStatusExtractor(
            "io.vertx.sqlclient.DatabaseException", "getSqlState", preserveNull);
    // older versions only have this method
    Function<Throwable, String> fallback =
        responseStatusExtractor("io.vertx.pgclient.PgException", "getCode", preserveNull);
    return error -> {
      String status = extractor.apply(error);
      return status != null ? status : fallback.apply(error);
    };
  }

  private static Function<Throwable, String> responseStatusExtractor(
      String className, String methodName, boolean preserveNull) {
    try {
      // loaded via reflection, because this class is not available in all versions that we support
      Class<?> exClass = Class.forName(className);
      Method method = exClass.getDeclaredMethod(methodName);

      return (error) -> {
        if (exClass.isInstance(error)) {
          try {
            Object status = method.invoke(error); // can be String or int
            return status == null && preserveNull ? null : String.valueOf(status);
          } catch (IllegalAccessException | InvocationTargetException ignored) {
            return null;
          }
        }
        return null;
      };
    } catch (ClassNotFoundException | NoSuchMethodException ignored) {
      return (error) -> null;
    }
  }
}
