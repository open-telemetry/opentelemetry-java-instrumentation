/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v4_0;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect.DOUBLE_QUOTES_ARE_IDENTIFIERS;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.javaagent.instrumentation.cassandra.v4_0.CassandraEndPoints.isSniEndPoint;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.cql.ExecutionInfo;
import com.datastax.oss.driver.api.core.metadata.EndPoint;
import com.datastax.oss.driver.api.core.metadata.Node;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlClientAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collection;
import javax.annotation.Nullable;

final class CassandraSqlAttributesGetter
    implements SqlClientAttributesGetter<CassandraRequest, ExecutionInfo> {

  private static final VirtualField<ExecutionInfo, InetSocketAddress> executionInfoPeer =
      VirtualField.find(ExecutionInfo.class, InetSocketAddress.class);

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
    CassandraServerTarget serverTarget = request.getServerTarget();
    return emitStableDatabaseSemconv() && serverTarget != null ? serverTarget.getAddress() : null;
  }

  @Override
  @Nullable
  public Integer getServerPort(CassandraRequest request) {
    CassandraServerTarget serverTarget = request.getServerTarget();
    return emitStableDatabaseSemconv() && serverTarget != null ? serverTarget.getPort() : null;
  }

  @Nullable
  @Override
  public InetSocketAddress getNetworkPeerInetSocketAddress(
      CassandraRequest request, @Nullable ExecutionInfo executionInfo) {
    if (executionInfo == null) {
      return null;
    }
    InetSocketAddress peer = executionInfoPeer.get(executionInfo);
    if (peer != null) {
      return peer;
    }
    Node coordinator = executionInfo.getCoordinator();
    if (coordinator == null) {
      return null;
    }
    EndPoint endPoint = coordinator.getEndPoint();
    if (!emitStableDatabaseSemconv() || !isSniEndPoint(endPoint)) {
      // Legacy semconv still records the proxy under SNI. Custom endpoints may be direct
      // connections.
      SocketAddress address = endPoint.resolve();
      return address instanceof InetSocketAddress ? (InetSocketAddress) address : null;
    }
    // SniEndPoint.resolve() performs DNS and advances the driver's shared round-robin counter, so
    // stable semconv leaves network.peer.* unset.
    return null;
  }

  @Override
  public boolean isParameterizedQuery(CassandraRequest request, int queryIndex) {
    return request.isParameterizedQuery(queryIndex);
  }
}
