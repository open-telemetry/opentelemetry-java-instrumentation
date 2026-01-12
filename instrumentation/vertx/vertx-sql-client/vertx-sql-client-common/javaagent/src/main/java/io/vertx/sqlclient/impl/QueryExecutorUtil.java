/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.vertx.sqlclient.impl;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.vertx.sqlclient.SqlConnectOptions;

// Helper class for accessing virtual field on package private QueryExecutor class.
public final class QueryExecutorUtil {
  private static final VirtualField<QueryExecutor<?, ?, ?>, SqlConnectOptions> connectOptionsFiled =
      VirtualField.find(QueryExecutor.class, SqlConnectOptions.class);

  public static void setConnectOptions(Object queryExecutor, SqlConnectOptions connectOptions) {
    connectOptionsFiled.set((QueryExecutor) queryExecutor, connectOptions);
  }

  public static SqlConnectOptions getConnectOptions(Object queryExecutor) {
    return connectOptionsFiled.get((QueryExecutor) queryExecutor);
  }

  private QueryExecutorUtil() {}
}
