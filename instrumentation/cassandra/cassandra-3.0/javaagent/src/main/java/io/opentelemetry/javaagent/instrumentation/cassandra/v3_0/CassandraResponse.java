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
      // SniEndPoint.resolve() returns the proxy, performs DNS, and advances the driver's shared
      // round-robin counter. Stable semconv uses the coordinator's broadcast address instead.
      InetSocketAddress rpcAddress = CassandraEndPoints.getBroadcastRpcAddress(coordinator);
      if (rpcAddress != null) {
        return new CassandraResponse(executionInfo, null, rpcAddress, null);
      }
      // Cloud deployments use the host id as the SNI name, not an address.
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
      // The exception exposes only the proxy endpoint and cannot distinguish a host id from an SNI
      // host name. Stable semconv therefore leaves the server and peer unset.
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
