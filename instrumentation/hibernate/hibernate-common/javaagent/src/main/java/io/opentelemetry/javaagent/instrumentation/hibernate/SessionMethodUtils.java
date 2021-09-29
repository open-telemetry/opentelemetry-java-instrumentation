/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate;

import static io.opentelemetry.javaagent.instrumentation.hibernate.HibernateTracer.tracer;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.db.SqlStatementInfo;
import io.opentelemetry.instrumentation.api.db.SqlStatementSanitizer;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import org.checkerframework.checker.nullness.qual.Nullable;

public final class SessionMethodUtils {

  public static final Set<String> SCOPE_ONLY_METHODS =
      new HashSet<>(Arrays.asList("immediateLoad", "internalLoad"));

  public static <TARGET, ENTITY> Context startSpanFrom(
      VirtualField<TARGET, Context> virtualField,
      TARGET spanKey,
      String operationName,
      String entityName) {
    return startSpanFrom(virtualField, spanKey, () -> operationName, entityName);
  }

  private static <TARGET, ENTITY> Context startSpanFrom(
      VirtualField<TARGET, Context> virtualField,
      TARGET spanKey,
      Supplier<String> operationNameSupplier,
      String entityName) {

    Context sessionContext = virtualField.get(spanKey);
    if (sessionContext == null) {
      return null; // No state found. We aren't in a Session.
    }

    return tracer().startSpan(sessionContext, operationNameSupplier.get(), entityName);
  }

  public static <TARGET> Context startSpanFromQuery(
      VirtualField<TARGET, Context> virtualField, TARGET spanKey, String query) {
    Supplier<String> operationNameSupplier =
        () -> {
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
        };
    return startSpanFrom(virtualField, spanKey, operationNameSupplier, null);
  }

  public static void end(@Nullable Context context, Throwable throwable) {

    if (context == null) {
      return;
    }

    if (throwable != null) {
      tracer().endExceptionally(context, throwable);
    } else {
      tracer().end(context);
    }
  }

  // Copies a span from the given Session VirtualField into the targetVirtualField. Used to
  // propagate a Span from a Session to transient Session objects such as Transaction and Query.
  public static <S, T> void attachSpanFromStore(
      VirtualField<S, Context> sourceVirtualField,
      S source,
      VirtualField<T, Context> targetVirtualField,
      T target) {

    Context sessionContext = sourceVirtualField.get(source);
    if (sessionContext == null) {
      return;
    }

    targetVirtualField.setIfNullAndGet(target, sessionContext);
  }

  public static String getSessionMethodSpanName(String methodName) {
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

  private SessionMethodUtils() {}
}
