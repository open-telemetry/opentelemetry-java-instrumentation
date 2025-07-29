/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.cassandra.v4_4;

import com.datastax.oss.driver.api.core.cql.ExecutionInfo;
import com.datastax.oss.driver.api.core.metadata.EndPoint;
import com.datastax.oss.driver.api.core.metadata.Node;
import com.datastax.oss.driver.internal.core.metadata.DefaultEndPoint;
import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesGetter;
import java.net.InetSocketAddress;
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
    EndPoint endPoint = coordinator.getEndPoint();
    if (endPoint instanceof DefaultEndPoint) {
      // resolve() returns an existing InetSocketAddress, it does not do a dns resolve,
      return (InetSocketAddress) endPoint.resolve();
    }
    return null;
  }
}
