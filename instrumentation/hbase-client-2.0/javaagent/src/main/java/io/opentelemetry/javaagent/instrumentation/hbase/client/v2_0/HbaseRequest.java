/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.client.v2_0;

import com.google.auto.value.AutoValue;
import javax.annotation.Nullable;
import org.apache.hadoop.hbase.TableName;

@AutoValue
public abstract class HbaseRequest {

  public static HbaseRequest create(
      @Nullable String operation,
      @Nullable TableName tableName,
      @Nullable String user,
      @Nullable String host,
      @Nullable Integer port,
      @Nullable Long operationBatchSize) {
    return new AutoValue_HbaseRequest(operation, tableName, user, host, port, operationBatchSize);
  }

  @Nullable
  public abstract String getOperation();

  @Nullable
  public abstract TableName getTableName();

  @Nullable
  public abstract String getUser();

  @Nullable
  public abstract String getHost();

  @Nullable
  public abstract Integer getPort();

  @Nullable
  public abstract Long getOperationBatchSize();
}
