/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.client.common;

import io.opentelemetry.instrumentation.api.internal.ScopedThreadValue;
import javax.annotation.Nullable;
import org.apache.hadoop.hbase.TableName;

public class HbaseClientState {

  private static final ScopedThreadValue<TableName> currentTableName = new ScopedThreadValue<>();
  private static final ScopedThreadValue<RequestAndContext> currentRequestAndContext =
      new ScopedThreadValue<>();

  public static ScopedThreadValue<TableName> currentTableName() {
    return currentTableName;
  }

  @Nullable
  public static TableName getTableName() {
    return currentTableName.get();
  }

  public static ScopedThreadValue<RequestAndContext> currentRequestAndContext() {
    return currentRequestAndContext;
  }

  @Nullable
  public static RequestAndContext getRequestAndContext() {
    return currentRequestAndContext.get();
  }

  private HbaseClientState() {}
}
