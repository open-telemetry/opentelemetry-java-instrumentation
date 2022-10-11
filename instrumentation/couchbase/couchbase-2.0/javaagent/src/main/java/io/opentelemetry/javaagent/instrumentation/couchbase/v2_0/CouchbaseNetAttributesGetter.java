/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0;

import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;

public class CouchbaseNetAttributesGetter
    extends InetSocketAddressNetClientAttributesGetter<CouchbaseRequestInfo, Void> {
  @Nullable
  @Override
  public String transport(CouchbaseRequestInfo couchbaseRequest, @Nullable Void unused) {
    return couchbaseRequest.getPeerAddress() != null
        ? SemanticAttributes.NetTransportValues.IP_TCP
        : null;
  }

  @Nullable
  @Override
  public String peerName(CouchbaseRequestInfo couchbaseRequest) {
    return null;
  }

  @Nullable
  @Override
  public Integer peerPort(CouchbaseRequestInfo couchbaseRequest) {
    return null;
  }

  @Nullable
  @Override
  protected InetSocketAddress getPeerSocketAddress(
      CouchbaseRequestInfo couchbaseRequest, @Nullable Void unused) {
    SocketAddress peerAddress = couchbaseRequest.getPeerAddress();
    if (peerAddress instanceof InetSocketAddress) {
      return (InetSocketAddress) peerAddress;
    }
    return null;
  }
}
