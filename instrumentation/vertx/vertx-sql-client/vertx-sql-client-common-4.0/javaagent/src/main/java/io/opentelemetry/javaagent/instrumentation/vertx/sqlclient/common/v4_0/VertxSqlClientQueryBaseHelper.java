/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;

public final class VertxSqlClientQueryBaseHelper {

  private static final String QUERY_BASE_CLASS = "io.vertx.sqlclient.impl.QueryBase";
  private static final String QUERY_EXECUTOR_CLASS = "io.vertx.sqlclient.impl.QueryExecutor";

  private static final ClassValue<Field> builderFields =
      new ClassValue<Field>() {
        @Nullable
        @Override
        protected Field computeValue(Class<?> type) {
          return findBuilderField(type);
        }
      };
  @Nullable private static final Method findVirtualFieldMethod = findVirtualFieldMethod();
  private static final ClassValue<VirtualField<Object, Object>> dataFields =
      new ClassValue<VirtualField<Object, Object>>() {
        @Nullable
        @Override
        protected VirtualField<Object, Object> computeValue(Class<?> type) {
          return findDataVirtualField(type);
        }
      };

  @Nullable
  private static Field findBuilderField(Class<?> queryClass) {
    Class<?> queryBaseClass = findClass(queryClass, QUERY_BASE_CLASS);
    if (queryBaseClass == null) {
      return null;
    }
    try {
      Field field = queryBaseClass.getDeclaredField("builder");
      field.setAccessible(true);
      return field;
    } catch (Throwable ignored) {
      return null;
    }
  }

  @Nullable
  private static Method findVirtualFieldMethod() {
    try {
      return VirtualField.class.getMethod("find", Class.class, Class.class);
    } catch (Throwable ignored) {
      return null;
    }
  }

  @SuppressWarnings("unchecked") // VirtualField key type is resolved by name at runtime
  @Nullable
  private static VirtualField<Object, Object> findDataVirtualField(Class<?> type) {
    Class<?> queryExecutorClass = findClass(type, QUERY_EXECUTOR_CLASS);
    Method method = findVirtualFieldMethod;
    if (queryExecutorClass == null || method == null) {
      return null;
    }
    try {
      return (VirtualField<Object, Object>) method.invoke(null, queryExecutorClass, Object.class);
    } catch (Throwable ignored) {
      return null;
    }
  }

  public static void setData(Object queryExecutor, @Nullable Object data) {
    VirtualField<Object, Object> virtualField = dataFields.get(queryExecutor.getClass());
    if (virtualField != null) {
      virtualField.set(queryExecutor, data);
    }
  }

  @Nullable
  public static Object getData(Object queryExecutor) {
    VirtualField<Object, Object> virtualField = dataFields.get(queryExecutor.getClass());
    return virtualField != null ? virtualField.get(queryExecutor) : null;
  }

  @SuppressWarnings("unused")
  @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
  public static void copyOnExit(
      @Advice.This Object sourceQuery, @Advice.Return Object copiedQuery) {
    Object sourceExecutor = getQueryExecutor(sourceQuery);
    Object copiedExecutor = getQueryExecutor(copiedQuery);
    if (sourceExecutor != null && copiedExecutor != null) {
      setData(copiedExecutor, getData(sourceExecutor));
    }
  }

  @Nullable
  public static Object getQueryExecutor(Object query) {
    Field field = builderFields.get(query.getClass());
    if (field == null) {
      return null;
    }
    try {
      return field.get(query);
    } catch (Throwable ignored) {
      return null;
    }
  }

  @Nullable
  private static Class<?> findClass(Class<?> type, String className) {
    for (Class<?> current = type; current != null; current = current.getSuperclass()) {
      if (className.equals(current.getName())) {
        return current;
      }
    }
    return null;
  }

  private VertxSqlClientQueryBaseHelper() {}
}
