/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0;

import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesGetter;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;

public class CouchbaseNetworkAttributesGetter
    implements NetworkAttributesGetter<CouchbaseRequestInfo, Void> {

  @Nullable
  @Override
  public InetSocketAddress getNetworkPeerInetSocketAddress(
      CouchbaseRequestInfo couchbaseRequest, @Nullable Void unused) {
    SocketAddress peerAddress = couchbaseRequest.getPeerAddress();
    if (peerAddress instanceof InetSocketAddress) {
      return (InetSocketAddress) peerAddress;
    }
    return null;
  }
}
