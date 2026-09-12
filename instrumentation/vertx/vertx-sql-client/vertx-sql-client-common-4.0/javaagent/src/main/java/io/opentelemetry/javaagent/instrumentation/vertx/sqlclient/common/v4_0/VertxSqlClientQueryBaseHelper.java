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

  @Nullable private static final Field builderField = findBuilderField();
  @Nullable private static final VirtualField<Object, Object> DATA = findDataVirtualField();

  @Nullable
  private static Field findBuilderField() {
    try {
      Class<?> queryBaseClass =
          Class.forName(
              QUERY_BASE_CLASS, false, VertxSqlClientQueryBaseHelper.class.getClassLoader());
      Field field = queryBaseClass.getDeclaredField("builder");
      field.setAccessible(true);
      return field;
    } catch (Throwable ignored) {
      return null;
    }
  }

  @SuppressWarnings("unchecked") // VirtualField key type is resolved by name at runtime
  @Nullable
  private static VirtualField<Object, Object> findDataVirtualField() {
    try {
      Class<Object> queryExecutorClass =
          (Class<Object>)
              Class.forName(
                  QUERY_EXECUTOR_CLASS,
                  false,
                  VertxSqlClientQueryBaseHelper.class.getClassLoader());
      Method findMethod = VirtualField.class.getMethod("find", Class.class, Class.class);
      return (VirtualField<Object, Object>)
          findMethod.invoke(null, queryExecutorClass, Object.class);
    } catch (Throwable ignored) {
      return null;
    }
  }

  public static void setData(Object queryExecutor, @Nullable Object data) {
    VirtualField<Object, Object> virtualField = DATA;
    if (virtualField != null) {
      virtualField.set(queryExecutor, data);
    }
  }

  @Nullable
  public static Object getData(Object queryExecutor) {
    VirtualField<Object, Object> virtualField = DATA;
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
    Field field = builderField;
    if (field == null) {
      return null;
    }
    try {
      return field.get(query);
    } catch (Throwable ignored) {
      return null;
    }
  }

  private VertxSqlClientQueryBaseHelper() {}
}
