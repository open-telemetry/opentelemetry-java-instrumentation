/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.cassandra.v4_4;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.cql.ExecutionInfo;
import com.datastax.oss.driver.api.core.metadata.EndPoint;
import com.datastax.oss.driver.api.core.metadata.Node;
import com.datastax.oss.driver.internal.core.metadata.DefaultEndPoint;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlClientAttributesGetter;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

final class CassandraSqlAttributesGetter
    implements SqlClientAttributesGetter<CassandraRequest, ExecutionInfo> {
  // copied from DbIncubatingAttributes.DbSystemIncubatingValues
  private static final String CASSANDRA = "cassandra";

  @Override
  public String getDbSystem(CassandraRequest request) {
    return CASSANDRA;
  }

  @Deprecated
  @Override
  @Nullable
  public String getUser(CassandraRequest request) {
    return null;
  }

  @Override
  @Nullable
  public String getDbNamespace(CassandraRequest request) {
    return request.getSession().getKeyspace().map(CqlIdentifier::toString).orElse(null);
  }

  @Deprecated
  @Override
  @Nullable
  public String getConnectionString(CassandraRequest request) {
    return null;
  }

  @Override
  @Nullable
  public String getRawQueryText(CassandraRequest request) {
    return request.getQueryText();
  }

  @Override
  @Nullable
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
      // resolve() returns an existing InetSocketAddress, it does not do a dns resolve,
      return (InetSocketAddress) endPoint.resolve();
    }
    return null;
  }
}
