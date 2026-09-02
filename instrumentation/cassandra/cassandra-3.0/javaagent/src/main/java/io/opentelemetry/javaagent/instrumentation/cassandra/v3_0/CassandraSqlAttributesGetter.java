/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v3_0;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect.DOUBLE_QUOTES_ARE_IDENTIFIERS;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlClientAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect;
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
  public SqlDialect getSqlDialect(CassandraRequest request) {
    // "A string constant is an arbitrary sequence of characters enclosed by single-quote(')."
    // https://cassandra.apache.org/doc/stable/cassandra/developing/cql/definitions.html#constants
    return DOUBLE_QUOTES_ARE_IDENTIFIERS;
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
    CassandraConfiguredTarget configuredTarget = request.getConfiguredTarget();
    return configuredTarget == null ? null : configuredTarget.getAddress();
  }

  @Override
  @Nullable
  public Integer getServerPort(CassandraRequest request) {
    CassandraConfiguredTarget configuredTarget = request.getConfiguredTarget();
    return configuredTarget == null ? null : configuredTarget.getPort();
  }

  @Nullable
  @Override
  public InetSocketAddress getNetworkPeerInetSocketAddress(
      CassandraRequest request, @Nullable CassandraResponse response) {
    return response == null ? null : response.getPeerAddress();
  }

  @Override
  public boolean isParameterizedQuery(CassandraRequest request, int queryIndex) {
    return request.isParameterizedQuery(queryIndex);
  }
}
