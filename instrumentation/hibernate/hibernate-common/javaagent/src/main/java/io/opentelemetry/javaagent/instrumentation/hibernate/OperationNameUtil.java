/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlQuery;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlQuerySanitizer;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import java.util.function.Function;
import javax.annotation.Nullable;

public final class OperationNameUtil {

  private static final String FALLBACK_SPAN_NAME = "hibernate";

  private static final SqlQuerySanitizer sanitizer =
      SqlQuerySanitizer.create(AgentCommonConfig.get().isQuerySanitizationEnabled());

  // query could be HQL or SQL
  public static String getOperationNameForQuery(@Nullable String query) {
    if (emitStableDatabaseSemconv()) {
      if (query != null) {
        SqlQuery info = sanitizer.sanitizeWithSummary(query);
        String summary = info.getQuerySummary();
        if (summary != null) {
          return summary;
        }
      }
      return FALLBACK_SPAN_NAME;
    }
    return getOperationNameForQueryOldSemconv(query);
  }

  private static String getOperationNameForQueryOldSemconv(@Nullable String query) {
    // set operation to default value that is used when sql sanitizer fails to extract
    // operation name
    String operation = "Hibernate Query";
    SqlQuery info = sanitizer.sanitize(query);
    if (info.getOperationName() != null) {
      operation = info.getOperationName();
      if (info.getCollectionName() != null) {
        operation += " " + info.getCollectionName();
      } else if (info.getStoredProcedureName() != null) {
        operation += " " + info.getStoredProcedureName();
      }
    }
    return operation;
  }

  public static String getSessionMethodOperationName(String methodName) {
    if ("fireLock".equals(methodName)) {
      return "Session.lock";
    }
    return "Session." + methodName;
  }

  public static String getEntityName(
      String descriptor, Object arg0, Object arg1, Function<Object, String> nameFromEntity) {
    String entityName = null;
    // methods like save(String entityName, Object object)
    // that take entity name as first argument and entity as second
    // if given entity name is null compute it from entity object
    if (descriptor.startsWith("(Ljava/lang/String;Ljava/lang/Object;")) {
      entityName = arg0 == null ? nameFromEntity.apply(arg1) : (String) arg0;
      // methods like save(Object obj)
    } else if (descriptor.startsWith("(Ljava/lang/Object;")) {
      entityName = nameFromEntity.apply(arg0);
      // methods like get(String entityName, Serializable id)
    } else if (descriptor.startsWith("(Ljava/lang/String;")) {
      entityName = (String) arg0;
      // methods like get(Class entityClass, Serializable id)
    } else if (descriptor.startsWith("(Ljava/lang/Class;") && arg0 != null) {
      entityName = ((Class<?>) arg0).getName();
    }

    return entityName;
  }

  private OperationNameUtil() {}
}
