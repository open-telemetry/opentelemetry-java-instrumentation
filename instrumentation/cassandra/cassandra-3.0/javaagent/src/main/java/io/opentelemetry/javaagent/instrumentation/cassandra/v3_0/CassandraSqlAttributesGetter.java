/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v3_0;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect.DOUBLE_QUOTES_ARE_IDENTIFIERS;
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
