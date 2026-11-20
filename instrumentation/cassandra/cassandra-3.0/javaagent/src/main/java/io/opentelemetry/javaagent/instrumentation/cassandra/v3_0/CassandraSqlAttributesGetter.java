/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v3_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlClientAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues;
import java.net.InetSocketAddress;
import java.util.Collection;
import javax.annotation.Nullable;

final class CassandraSqlAttributesGetter
    implements SqlClientAttributesGetter<CassandraRequest, CassandraResponse> {

  @Override
  public String getDbSystemName(CassandraRequest request) {
    return DbSystemNameIncubatingValues.CASSANDRA;
  }

  @Override
  @Nullable
  public String getDbNamespace(CassandraRequest request) {
    return request.getSession().getLoggedKeyspace();
  }

  @Override
  public Collection<String> getRawQueryTexts(CassandraRequest request) {
    return request.getQueryTexts();
  }

  @Override
  @Nullable
  public Long getDbOperationBatchSize(CassandraRequest request) {
    return request.getBatchSize();
  }

  @Override
  @Nullable
  public String getServerAddress(CassandraRequest request) {
    DbServerTarget serverTarget = request.getServerTarget();
    return serverTarget == null ? null : serverTarget.getAddress();
  }

  @Override
  @Nullable
  public Integer getServerPort(CassandraRequest request) {
    DbServerTarget serverTarget = request.getServerTarget();
    return serverTarget == null ? null : serverTarget.getPort();
  }

  @Nullable
  @Override
  public InetSocketAddress getNetworkPeerSocketAddress(
      CassandraRequest request, @Nullable CassandraResponse response) {
    return response == null ? null : response.getPeerAddress();
  }

  @Override
  public boolean isParameterizedQuery(CassandraRequest request, int queryIndex) {
    return request.isParameterizedQuery(queryIndex);
  }
}
