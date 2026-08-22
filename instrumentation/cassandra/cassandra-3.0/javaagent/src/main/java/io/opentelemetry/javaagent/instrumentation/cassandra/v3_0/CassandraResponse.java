/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v3_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;

import com.datastax.driver.core.ExecutionInfo;
import com.datastax.driver.core.Host;
import com.datastax.driver.core.exceptions.CoordinatorException;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

class CassandraResponse {
  @Nullable private final ExecutionInfo executionInfo;
  @Nullable private final InetSocketAddress peerAddress;
  @Nullable private final InetSocketAddress serverAddress;
  @Nullable private final String serverName;

  private CassandraResponse(
      @Nullable ExecutionInfo executionInfo,
      @Nullable InetSocketAddress peerAddress,
      @Nullable InetSocketAddress serverAddress,
      @Nullable String serverName) {
    this.executionInfo = executionInfo;
    this.peerAddress = peerAddress;
    this.serverAddress = serverAddress;
    this.serverName = serverName;
  }

  static CassandraResponse create(ExecutionInfo executionInfo) {
    Host coordinator = executionInfo.getQueriedHost();
    if (coordinator == null) {
      return new CassandraResponse(executionInfo, null, null, null);
    }
    if (emitStableDatabaseSemconv() && CassandraEndPoints.isSniEndPoint(coordinator)) {
      // Under SNI (proxied deployments such as DataStax Astra) the client connects to a proxy, so
      // the coordinator's socket address is the proxy rather than the server behind it. Reading it
      // also calls SniEndPoint.resolve(), which rotates a shared static counter the driver uses to
      // pick a connection, and performs a dns lookup whenever the proxy address is unresolved,
      // which it is for cloud deployments. Record the coordinator's own broadcast rpc address as
      // the server, and leave the peer unset because the proxy socket is only reachable through
      // resolve(). This applies only under the stable database semantic conventions; the old
      // conventions are frozen and keep recording the proxy below.
      InetSocketAddress rpcAddress = CassandraEndPoints.getBroadcastRpcAddress(coordinator);
      if (rpcAddress != null) {
        return new CassandraResponse(executionInfo, null, rpcAddress, null);
      }
      // When the node has not published its rpc address, fall back to the SNI server name, which
      // carries no port. In cloud deployments the driver sets that name to the node's host id,
      // which is an opaque identifier rather than an address, and which is already recorded as
      // cassandra.coordinator.id. Keep the server name only when it is something else, such as a
      // host name supplied for a custom SNI proxy.
      String serverName = CassandraEndPoints.getSniServerName(coordinator);
      String hostId = CassandraEndPoints.getHostId(coordinator);
      if (hostId != null && hostId.equals(serverName)) {
        serverName = null;
      }
      return new CassandraResponse(executionInfo, null, null, serverName);
    }
    InetSocketAddress address = coordinator.getSocketAddress();
    return new CassandraResponse(executionInfo, address, address, null);
  }

  @Nullable
  static CassandraResponse create(Throwable throwable) {
    if (!(throwable instanceof CoordinatorException)) {
      return null;
    }
    CoordinatorException exception = (CoordinatorException) throwable;
    if (emitStableDatabaseSemconv() && CassandraEndPoints.isSniEndPoint(exception)) {
      // the exception knows only the proxy endpoint, and getAddress() would resolve it, so neither
      // address is recorded. The endpoint carries an SNI server name, but the exception carries no
      // host id to tell an opaque cloud server name from a real host name, so that is not recorded
      // either. This applies only under the stable database semantic conventions; the old
      // conventions are frozen and keep recording the proxy below.
      return new CassandraResponse(null, null, null, null);
    }
    InetSocketAddress address = exception.getAddress();
    return new CassandraResponse(null, address, address, null);
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

  @Nullable
  String getServerName() {
    return serverName;
  }
}
