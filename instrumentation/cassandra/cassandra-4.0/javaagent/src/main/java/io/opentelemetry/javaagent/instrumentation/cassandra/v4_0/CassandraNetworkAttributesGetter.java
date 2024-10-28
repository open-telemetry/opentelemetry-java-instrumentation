/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v4_0;

import com.datastax.oss.driver.api.core.cql.ExecutionInfo;
import com.datastax.oss.driver.api.core.metadata.Node;
import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesGetter;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;

final class CassandraNetworkAttributesGetter
    implements NetworkAttributesGetter<CassandraRequest, ExecutionInfo> {

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
