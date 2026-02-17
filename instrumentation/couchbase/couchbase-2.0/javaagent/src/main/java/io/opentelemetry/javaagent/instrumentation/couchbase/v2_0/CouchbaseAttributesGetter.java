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
  public String getDbSystemName(CouchbaseRequestInfo couchbaseRequest) {
    return DbIncubatingAttributes.DbSystemNameIncubatingValues.COUCHBASE;
  }

  @Override
  @Nullable
  public String getDbNamespace(CouchbaseRequestInfo couchbaseRequest) {
    return couchbaseRequest.bucket();
  }

  @Override
  @Nullable
  public String getDbQueryText(CouchbaseRequestInfo couchbaseRequest) {
    if (couchbaseRequest.getSqlQueryWithSummary() != null) {
      return couchbaseRequest.getSqlQueryWithSummary().getQueryText();
    }
    if (couchbaseRequest.getSqlQuery() != null) {
      return couchbaseRequest.getSqlQuery().getQueryText();
    }
    return null;
  }

  @Override
  @Nullable
  public String getDbQuerySummary(CouchbaseRequestInfo couchbaseRequest) {
    if (couchbaseRequest.getSqlQueryWithSummary() != null) {
      return couchbaseRequest.getSqlQueryWithSummary().getQuerySummary();
    }
    return null;
  }

  @Override
  @Nullable
  public String getDbOperationName(CouchbaseRequestInfo couchbaseRequest) {
    return couchbaseRequest.operation();
  }

  @Override
  public InetSocketAddress getNetworkPeerInetSocketAddress(
      CouchbaseRequestInfo request, @Nullable Void unused) {
    SocketAddress address = request.getPeerAddress();
    if (address instanceof InetSocketAddress) {
      return (InetSocketAddress) address;
    }
    return null;
  }
}
