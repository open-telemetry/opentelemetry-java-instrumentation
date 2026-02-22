/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v3_0;

import static java.util.Collections.singleton;

import com.datastax.driver.core.ExecutionInfo;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlClientAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import java.net.InetSocketAddress;
import java.util.Collection;
import javax.annotation.Nullable;

final class CassandraSqlAttributesGetter
    implements SqlClientAttributesGetter<CassandraRequest, ExecutionInfo> {

  @Override
  public String getDbSystemName(CassandraRequest request) {
    return DbIncubatingAttributes.DbSystemNameIncubatingValues.CASSANDRA;
  }

  @Override
  public SqlDialect getSqlDialect(CassandraRequest request) {
    return SqlDialect.CASSANDRA;
  }

  @Override
  @Nullable
  public String getDbNamespace(CassandraRequest request) {
    return request.getSession().getLoggedKeyspace();
  }

  @Override
  public Collection<String> getRawQueryTexts(CassandraRequest request) {
    return singleton(request.getQueryText());
  }

  @Nullable
  @Override
  public InetSocketAddress getNetworkPeerInetSocketAddress(
      CassandraRequest request, @Nullable ExecutionInfo executionInfo) {
    return executionInfo == null ? null : executionInfo.getQueriedHost().getSocketAddress();
  }

  @Override
  public boolean isParameterizedQuery(CassandraRequest request) {
    return request.isParameterizedQuery();
  }
}
