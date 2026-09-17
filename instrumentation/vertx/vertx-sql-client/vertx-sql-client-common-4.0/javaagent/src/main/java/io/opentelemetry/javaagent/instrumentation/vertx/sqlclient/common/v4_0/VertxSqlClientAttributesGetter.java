/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.SqlDialectUtil.fromDbSystemName;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static java.util.Collections.singleton;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlClientAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.function.Function;
import javax.annotation.Nullable;

class VertxSqlClientAttributesGetter
    implements SqlClientAttributesGetter<VertxSqlClientRequest, Void> {

  private static final Function<Throwable, String> responseStatusExtractor =
      createResponseStatusExtractor();

  @Override
  public String getDbSystemName(VertxSqlClientRequest request) {
    return request.getDbSystemName();
  }

  @Deprecated // to be removed in 3.0
  @Override
  @Nullable
  public String getDbSystem(VertxSqlClientRequest request) {
    // preserving old behavior: db.system was never set for vertx sql client
    return null;
  }

  @Override
  public SqlDialect getSqlDialect(VertxSqlClientRequest request) {
    return fromDbSystemName(request.getDbSystemName());
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
    if (emitStableDatabaseSemconv() && request.isServerTargetCaptured()) {
      return request.getConfiguredServerAddress();
    }
    return request.getHost();
  }

  @Nullable
  @Override
  public Integer getServerPort(VertxSqlClientRequest request) {
    if (emitStableDatabaseSemconv() && request.isServerTargetCaptured()) {
      return request.getConfiguredServerPort();
    }
    return request.getPort();
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

  @Override
  public boolean isParameterizedQuery(VertxSqlClientRequest request, int queryIndex) {
    // Vert.x SQL client does not support mixed parameterization within a single request.
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
