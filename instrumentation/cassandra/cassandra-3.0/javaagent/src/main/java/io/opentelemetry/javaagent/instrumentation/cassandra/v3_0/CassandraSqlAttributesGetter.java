/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v3_0;

import com.datastax.driver.core.ExecutionInfo;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlClientAttributesGetter;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

final class CassandraSqlAttributesGetter
    implements SqlClientAttributesGetter<CassandraRequest, ExecutionInfo> {

  @Override
  public String getDbSystem(CassandraRequest request) {
    return DbIncubatingAttributes.DbSystemIncubatingValues.CASSANDRA;
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
    return request.getSession().getLoggedKeyspace();
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
    return executionInfo == null ? null : executionInfo.getQueriedHost().getSocketAddress();
  }
}
