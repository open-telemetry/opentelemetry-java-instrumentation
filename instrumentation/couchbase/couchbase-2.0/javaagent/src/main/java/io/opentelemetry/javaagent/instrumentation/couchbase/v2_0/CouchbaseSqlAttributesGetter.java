/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlClientAttributesGetter;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v2_0.CouchbaseRequestInfo;
import java.net.InetSocketAddress;
import java.util.Collection;
import javax.annotation.Nullable;

final class CouchbaseSqlAttributesGetter
    implements SqlClientAttributesGetter<CouchbaseRequestInfo, Void> {

  private final CouchbaseAttributesGetter dbGetter = new CouchbaseAttributesGetter();

  @Override
  public Collection<String> getRawQueryTexts(CouchbaseRequestInfo request) {
    String queryText = request.getQueryText();
    return queryText == null ? emptyList() : singletonList(queryText);
  }

  @Override
  public String getDbSystemName(CouchbaseRequestInfo request) {
    return dbGetter.getDbSystemName(request);
  }

  @Override
  @Nullable
  public String getDbNamespace(CouchbaseRequestInfo request) {
    return dbGetter.getDbNamespace(request);
  }

  @Override
  @Nullable
  public String getServerAddress(CouchbaseRequestInfo request) {
    return dbGetter.getServerAddress(request);
  }

  @Override
  @Nullable
  public Integer getServerPort(CouchbaseRequestInfo request) {
    return dbGetter.getServerPort(request);
  }

  @Override
  @Nullable
  public InetSocketAddress getNetworkPeerInetSocketAddress(
      CouchbaseRequestInfo request, @Nullable Void unused) {
    return dbGetter.getNetworkPeerInetSocketAddress(request, null);
  }
}
