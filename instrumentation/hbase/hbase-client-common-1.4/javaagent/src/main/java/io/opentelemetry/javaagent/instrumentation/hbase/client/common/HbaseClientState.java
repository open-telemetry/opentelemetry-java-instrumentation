/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.client.common;

import javax.annotation.Nullable;
import org.apache.hadoop.hbase.TableName;

public class HbaseClientState {

  private static final ThreadLocal<TableName> tableNameThreadLocal = new ThreadLocal<>();
  private static final ThreadLocal<RequestAndContext> requestAndContextThreadLocal =
      new ThreadLocal<>();

  public static void setTableName(TableName tableName) {
    tableNameThreadLocal.set(tableName);
  }

  @Nullable
  public static TableName getTableName() {
    return tableNameThreadLocal.get();
  }

  public static void resetTableName() {
    tableNameThreadLocal.remove();
  }

  public static void setRequestAndContext(RequestAndContext requestAndContext) {
    requestAndContextThreadLocal.set(requestAndContext);
  }

  @Nullable
  public static RequestAndContext getRequestAndContext() {
    return requestAndContextThreadLocal.get();
  }

  public static void resetRequestAndContext() {
    requestAndContextThreadLocal.remove();
  }

  private HbaseClientState() {}
}
