/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.cassandra.v4_4;

import com.datastax.oss.driver.api.core.cql.ExecutionInfo;
import com.datastax.oss.driver.api.core.metadata.Node;
import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesGetter;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesGetter;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;

final class CassandraNetworkAttributesGetter
    implements ServerAttributesGetter<CassandraRequest>,
        NetworkAttributesGetter<CassandraRequest, ExecutionInfo> {

  @Nullable
  @Override
  public String getServerAddress(CassandraRequest request) {
    SocketAddress socketAddress =
        request
            .getSession()
            .getMetadata()
            .getNodes()
            .values()
            .iterator()
            .next()
            .getEndPoint()
            .resolve();
    if (socketAddress instanceof InetSocketAddress) {
      return ((InetSocketAddress) socketAddress).getHostName();
    }
    return null;
  }

  @Nullable
  @Override
  public Integer getServerPort(CassandraRequest request) {
    SocketAddress socketAddress =
        request
            .getSession()
            .getMetadata()
            .getNodes()
            .values()
            .iterator()
            .next()
            .getEndPoint()
            .resolve();
    if (socketAddress instanceof InetSocketAddress) {
      return ((InetSocketAddress) socketAddress).getPort();
    }
    return null;
  }

  @Override
  @Nullable
  public InetSocketAddress getNetworkPeerInetSocketAddress(
      CassandraRequest request, @Nullable ExecutionInfo executionInfo) {
    if (executionInfo == null) {
      return null;
    }
    Node coordinator = executionInfo.getCoordinator();
    if (coordinator == null) {
      return null;
    }
    // resolve() returns an existing InetSocketAddress, it does not do a dns resolve,
    // at least in the only current EndPoint implementation (DefaultEndPoint)
    SocketAddress address = coordinator.getEndPoint().resolve();
    return address instanceof InetSocketAddress ? (InetSocketAddress) address : null;
  }
}
