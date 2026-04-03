/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.vertx.sqlclient.impl;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.vertx.sqlclient.SqlConnectOptions;
import javax.annotation.Nullable;

// Helper class for accessing virtual field on package private QueryExecutor class.
public final class QueryExecutorUtil {
  private static final VirtualField<QueryExecutor<?, ?, ?>, SqlConnectOptions> connectOptionsField =
      VirtualField.find(QueryExecutor.class, SqlConnectOptions.class);

  public static void setConnectOptions(
      Object queryExecutor, @Nullable SqlConnectOptions connectOptions) {
    connectOptionsField.set((QueryExecutor) queryExecutor, connectOptions);
  }

  @Nullable
  public static SqlConnectOptions getConnectOptions(Object queryExecutor) {
    return connectOptionsField.get((QueryExecutor) queryExecutor);
  }

  private QueryExecutorUtil() {}
}
