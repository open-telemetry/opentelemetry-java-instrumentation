/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.client.common.v1_0;

import javax.annotation.Nullable;
import org.apache.hadoop.hbase.TableName;

public class HbaseClientState {

  private static final ThreadLocal<TableName> currentTableName = new ThreadLocal<>();
  private static final ThreadLocal<RequestAndContext> currentRequestAndContext =
      new ThreadLocal<>();

  public static void setTableName(TableName tableName) {
    currentTableName.set(tableName);
  }

  @Nullable
  public static TableName getTableName() {
    return currentTableName.get();
  }

  public static void resetTableName() {
    currentTableName.remove();
  }

  public static void setRequestAndContext(RequestAndContext requestAndContext) {
    currentRequestAndContext.set(requestAndContext);
  }

  @Nullable
  public static RequestAndContext getRequestAndContext() {
    return currentRequestAndContext.get();
  }

  public static void resetRequestAndContext() {
    currentRequestAndContext.remove();
  }

  private HbaseClientState() {}
}
