/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.client.common;

import com.google.auto.value.AutoValue;
import javax.annotation.Nullable;
import org.apache.hadoop.hbase.TableName;

@AutoValue
public abstract class HbaseRequest {

  public static HbaseRequest create(
      @Nullable String operation,
      @Nullable TableName tableName,
      @Nullable String user,
      @Nullable String serverAddress,
      @Nullable Integer serverPort,
      @Nullable String serverTarget,
      @Nullable Long operationBatchSize) {
    return new AutoValue_HbaseRequest(
        operation,
        tableName,
        user,
        serverAddress,
        serverPort,
        serverTarget,
        null,
        null,
        operationBatchSize);
  }

  HbaseRequest withNetworkPeer(String networkPeerAddress, int networkPeerPort) {
    return new AutoValue_HbaseRequest(
        getOperation(),
        getTableName(),
        getUser(),
        getServerAddress(),
        getServerPort(),
        getServerTarget(),
        networkPeerAddress,
        networkPeerPort,
        getOperationBatchSize());
  }

  @Nullable
  public abstract String getOperation();

  @Nullable
  public abstract TableName getTableName();

  @Nullable
  public abstract String getUser();

  @Nullable
  public abstract String getServerAddress();

  @Nullable
  public abstract Integer getServerPort();

  @Nullable
  public abstract String getServerTarget();

  @Nullable
  public abstract String getNetworkPeerAddress();

  @Nullable
  public abstract Integer getNetworkPeerPort();

  @Nullable
  public abstract Long getOperationBatchSize();
}
