/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.cassandra.v4_4;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect.DOUBLE_QUOTES_ARE_IDENTIFIERS;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.cassandra.v4_4.internal.CassandraNetworkPeer.get;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.cql.ExecutionInfo;
import com.datastax.oss.driver.api.core.metadata.EndPoint;
import com.datastax.oss.driver.api.core.metadata.Node;
import com.datastax.oss.driver.internal.core.metadata.DefaultEndPoint;
import com.datastax.oss.driver.internal.core.metadata.SniEndPoint;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlClientAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect;
import java.net.InetSocketAddress;
import java.util.Collection;
import javax.annotation.Nullable;

final class CassandraSqlAttributesGetter
    implements SqlClientAttributesGetter<CassandraRequest, ExecutionInfo> {
  // copied from DbIncubatingAttributes.DbSystemNameIncubatingValues
  private static final String CASSANDRA = "cassandra";

  @Override
  public String getDbSystemName(CassandraRequest request) {
    return CASSANDRA;
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
    return request.getSession().getKeyspace().map(CqlIdentifier::toString).orElse(null);
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
    CassandraServerTarget serverTarget = getServerTarget(request);
    return serverTarget == null ? null : serverTarget.getAddress();
  }

  @Override
  @Nullable
  public Integer getServerPort(CassandraRequest request) {
    CassandraServerTarget serverTarget = getServerTarget(request);
    return serverTarget == null ? null : serverTarget.getPort();
  }

  @Nullable
  @Override
  public InetSocketAddress getNetworkPeerInetSocketAddress(
      CassandraRequest request, @Nullable ExecutionInfo executionInfo) {
    if (executionInfo == null) {
      return null;
    }
    InetSocketAddress peer = get(executionInfo);
    if (peer != null) {
      return peer;
    }
    Node coordinator = executionInfo.getCoordinator();
    if (coordinator == null) {
      return null;
    }
    EndPoint endPoint = coordinator.getEndPoint();
    if (endPoint instanceof DefaultEndPoint) {
      // resolve() returns the already-resolved InetSocketAddress, it does not do a dns lookup
      return (InetSocketAddress) endPoint.resolve();
    }
    return null;
  }

  @Override
  public boolean isParameterizedQuery(CassandraRequest request, int queryIndex) {
    return request.isParameterizedQuery(queryIndex);
  }

  @Nullable
  private static CassandraServerTarget getServerTarget(CassandraRequest request) {
    if (!emitStableDatabaseSemconv()) {
      return null;
    }
    for (Node node : request.getSession().getMetadata().getNodes().values()) {
      if (node.getEndPoint() instanceof SniEndPoint) {
        return null;
      }
    }
    return request.getServerTarget();
  }
}
