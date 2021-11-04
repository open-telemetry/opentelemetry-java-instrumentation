/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate;

import io.opentelemetry.instrumentation.api.db.SqlStatementInfo;
import io.opentelemetry.instrumentation.api.db.SqlStatementSanitizer;
import java.util.function.Function;

public final class OperationNameUtil {

  public static String getOperationNameForQuery(String query) {
    // set operation to default value that is used when sql sanitizer fails to extract
    // operation name
    String operation = "Hibernate Query";
    SqlStatementInfo info = SqlStatementSanitizer.sanitize(query);
    if (info.getOperation() != null) {
      operation = info.getOperation();
      if (info.getTable() != null) {
        operation += " " + info.getTable();
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
