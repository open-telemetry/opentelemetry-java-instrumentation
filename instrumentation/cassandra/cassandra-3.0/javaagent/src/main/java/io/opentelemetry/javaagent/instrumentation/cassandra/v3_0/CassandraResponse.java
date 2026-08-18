/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v3_0;

import com.datastax.driver.core.ExecutionInfo;
import com.datastax.driver.core.Host;
import com.datastax.driver.core.exceptions.CoordinatorException;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

class CassandraResponse {
  @Nullable private final ExecutionInfo executionInfo;
  @Nullable private final InetSocketAddress peerAddress;
  @Nullable private final InetSocketAddress serverAddress;

  private CassandraResponse(
      @Nullable ExecutionInfo executionInfo,
      @Nullable InetSocketAddress peerAddress,
      @Nullable InetSocketAddress serverAddress) {
    this.executionInfo = executionInfo;
    this.peerAddress = peerAddress;
    this.serverAddress = serverAddress;
  }

  static CassandraResponse create(ExecutionInfo executionInfo) {
    Host coordinator = executionInfo.getQueriedHost();
    if (coordinator == null) {
      return new CassandraResponse(executionInfo, null, null);
    }
    if (CassandraEndPoints.isSniEndPoint(coordinator)) {
      // Under SNI (proxied deployments such as DataStax Astra) the client connects to a proxy, so
      // the coordinator's socket address is the proxy rather than the server behind it. Reading it
      // also calls SniEndPoint.resolve(), which performs a dns lookup on every call and rotates a
      // shared static counter the driver uses to pick a connection. Record the coordinator's own
      // broadcast rpc address as the server, and leave the peer unset because the proxy socket is
      // only reachable through resolve().
      return new CassandraResponse(
          executionInfo, null, CassandraEndPoints.getBroadcastRpcAddress(coordinator));
    }
    InetSocketAddress address = coordinator.getSocketAddress();
    return new CassandraResponse(executionInfo, address, address);
  }

  @Nullable
  static CassandraResponse create(Throwable throwable) {
    if (!(throwable instanceof CoordinatorException)) {
      return null;
    }
    CoordinatorException exception = (CoordinatorException) throwable;
    if (CassandraEndPoints.isSniEndPoint(exception)) {
      // the exception knows only the proxy endpoint, and getAddress() would resolve it, so neither
      // address is recorded
      return new CassandraResponse(null, null, null);
    }
    InetSocketAddress address = exception.getAddress();
    return new CassandraResponse(null, address, address);
  }

  @Nullable
  ExecutionInfo getExecutionInfo() {
    return executionInfo;
  }

  @Nullable
  InetSocketAddress getPeerAddress() {
    return peerAddress;
  }

  @Nullable
  InetSocketAddress getServerAddress() {
    return serverAddress;
  }
}
