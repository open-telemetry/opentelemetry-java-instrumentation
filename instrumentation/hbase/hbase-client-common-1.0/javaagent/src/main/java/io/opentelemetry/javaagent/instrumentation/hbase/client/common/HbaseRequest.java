/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.client.common;

import com.google.auto.value.AutoValue;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;
import org.apache.hadoop.hbase.TableName;

@AutoValue
public abstract class HbaseRequest {

  // Retries create a new Call/request, and each Call is sent through one connection.
  @Nullable private volatile InetSocketAddress networkPeer;

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

  public void setNetworkPeer(InetSocketAddress networkPeer) {
    this.networkPeer = networkPeer;
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
    InetSocketAddress networkPeer = this.networkPeer;
    return networkPeer == null ? null : networkPeer.getAddress().getHostAddress();
  }

  @Nullable
  public Integer getNetworkPeerPort() {
    InetSocketAddress networkPeer = this.networkPeer;
    return networkPeer == null ? null : networkPeer.getPort();
  }

  @Nullable
  public abstract Long getOperationBatchSize();
}
