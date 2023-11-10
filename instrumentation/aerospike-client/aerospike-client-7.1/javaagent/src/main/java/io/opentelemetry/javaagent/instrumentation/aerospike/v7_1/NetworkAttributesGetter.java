/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.aerospike.v7_1;

import com.aerospike.client.cluster.Node;
import io.opentelemetry.instrumentation.api.instrumenter.network.ServerAttributesGetter;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

final class NetworkAttributesGetter
    implements ServerAttributesGetter<AerospikeRequest, Void>,
        io.opentelemetry.instrumentation.api.instrumenter.network.NetworkAttributesGetter<
            AerospikeRequest, Void> {

  @Override
  @Nullable
  public InetSocketAddress getNetworkPeerInetSocketAddress(
      AerospikeRequest aerospikeRequest, @Nullable Void unused) {
    Node node = aerospikeRequest.getNode();
    if (node != null) {
      return node.getAddress();
    }
    return null;
  }
}
