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

  @Nullable private String networkPeerAddress;
  @Nullable private Integer networkPeerPort;

  public static HbaseRequest create(
      @Nullable String operation,
      @Nullable TableName tableName,
      @Nullable String user,
      @Nullable String serverAddress,
      @Nullable Integer serverPort,
      @Nullable String serverTarget,
      @Nullable Long operationBatchSize) {
    return new AutoValue_HbaseRequest(
        operation, tableName, user, serverAddress, serverPort, serverTarget, operationBatchSize);
  }

  public void setNetworkPeer(String networkPeerAddress, int networkPeerPort) {
    this.networkPeerAddress = networkPeerAddress;
    this.networkPeerPort = networkPeerPort;
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
  public String getNetworkPeerAddress() {
    return networkPeerAddress;
  }

  @Nullable
  public Integer getNetworkPeerPort() {
    return networkPeerPort;
  }

  @Nullable
  public abstract Long getOperationBatchSize();
}
