/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;

public class CouchbaseNetAttributesGetter
    implements NetClientAttributesGetter<CouchbaseRequestInfo, Void> {

  @Nullable
  @Override
  public String getPeerName(CouchbaseRequestInfo couchbaseRequest) {
    return null;
  }

  @Nullable
  @Override
  public Integer getPeerPort(CouchbaseRequestInfo couchbaseRequest) {
    return null;
  }

  @Nullable
  @Override
  public InetSocketAddress getPeerSocketAddress(
      CouchbaseRequestInfo couchbaseRequest, @Nullable Void unused) {
    SocketAddress peerAddress = couchbaseRequest.getPeerAddress();
    if (peerAddress instanceof InetSocketAddress) {
      return (InetSocketAddress) peerAddress;
    }
    return null;
  }
}
