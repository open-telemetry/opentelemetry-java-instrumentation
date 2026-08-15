/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.vertx.sqlclient.impl;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import javax.annotation.Nullable;

// Helper class for accessing virtual field on package private QueryExecutor class. This class is
// injected into the application class loader, which can not see the instrumentation helper classes,
// so the virtual field is typed as Object and callers are responsible for the cast.
public class QueryExecutorUtil {
  private static final VirtualField<QueryExecutor<?, ?, ?>, Object> DATA =
      VirtualField.find(QueryExecutor.class, Object.class);

  public static void setData(Object queryExecutor, @Nullable Object data) {
    DATA.set((QueryExecutor<?, ?, ?>) queryExecutor, data);
  }

  @Nullable
  public static Object getData(Object queryExecutor) {
    return DATA.get((QueryExecutor<?, ?, ?>) queryExecutor);
  }

  public static void copyQueryExecutorData(Object sourceQuery, Object copiedQuery) {
    QueryExecutor<?, ?, ?> sourceExecutor = ((QueryBase<?, ?>) sourceQuery).builder;
    QueryExecutor<?, ?, ?> copiedExecutor = ((QueryBase<?, ?>) copiedQuery).builder;
    DATA.set(copiedExecutor, DATA.get(sourceExecutor));
  }

  private QueryExecutorUtil() {}
}
