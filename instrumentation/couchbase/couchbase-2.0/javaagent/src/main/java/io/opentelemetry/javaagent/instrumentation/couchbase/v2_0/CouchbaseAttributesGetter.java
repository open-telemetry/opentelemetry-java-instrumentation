/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;

final class CouchbaseAttributesGetter
    implements DbClientAttributesGetter<CouchbaseRequestInfo, Void> {

  @Override
  public String getDbSystem(CouchbaseRequestInfo couchbaseRequest) {
    return DbIncubatingAttributes.DbSystemIncubatingValues.COUCHBASE;
  }

  @Deprecated
  @Override
  @Nullable
  public String getUser(CouchbaseRequestInfo couchbaseRequest) {
    return null;
  }

  @Override
  @Nullable
  public String getDbNamespace(CouchbaseRequestInfo couchbaseRequest) {
    return couchbaseRequest.bucket();
  }

  @Deprecated
  @Override
  @Nullable
  public String getConnectionString(CouchbaseRequestInfo couchbaseRequest) {
    return null;
  }

  @Override
  @Nullable
  public String getDbQueryText(CouchbaseRequestInfo couchbaseRequest) {
    return couchbaseRequest.statement();
  }

  @Override
  @Nullable
  public String getDbOperationName(CouchbaseRequestInfo couchbaseRequest) {
    return couchbaseRequest.operation();
  }

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
