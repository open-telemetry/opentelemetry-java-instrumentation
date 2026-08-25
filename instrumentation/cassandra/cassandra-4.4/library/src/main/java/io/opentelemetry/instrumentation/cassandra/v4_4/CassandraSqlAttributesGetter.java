/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.cassandra.v4_4;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect.DOUBLE_QUOTES_ARE_IDENTIFIERS;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.cql.ExecutionInfo;
import com.datastax.oss.driver.api.core.metadata.EndPoint;
import com.datastax.oss.driver.api.core.metadata.Node;
import com.datastax.oss.driver.internal.core.metadata.DefaultEndPoint;
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

  @Nullable
  @Override
  public InetSocketAddress getNetworkPeerInetSocketAddress(
      CassandraRequest request, @Nullable ExecutionInfo executionInfo) {
    if (executionInfo == null) {
      return null;
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
    // Every other endpoint kind, including SNI (proxied deployments such as DataStax Astra), gets
    // no network.peer.*. The peer is the proxy, but no address for it is worth recording. The only
    // public accessor, SniEndPoint.resolve(), performs a dns lookup on every call and round-robins
    // across the resolved addresses using a shared static counter that the driver also uses to pick
    // a connection, so calling it here would add a per-span dns lookup, record a rotating address
    // that may not match the connection, and perturb the driver's own rotation. The private
    // proxyAddress field that the attributes extractor reads for the old conventions is unresolved
    // for cloud deployments, so it carries no ip address to record either.
    return null;
  }

  @Override
  public boolean isParameterizedQuery(CassandraRequest request, int queryIndex) {
    return request.isParameterizedQuery(queryIndex);
  }
}
