/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v3_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlClientAttributesGetter;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import javax.annotation.Nullable;

final class CassandraSqlAttributesGetter implements SqlClientAttributesGetter<CassandraRequest> {

  @Override
  public String getSystem(CassandraRequest request) {
    return DbIncubatingAttributes.DbSystemValues.CASSANDRA;
  }

  @Deprecated
  @Override
  @Nullable
  public String getUser(CassandraRequest request) {
    return null;
  }

  @Deprecated
  @Override
  @Nullable
  public String getName(CassandraRequest request) {
    return request.getSession().getLoggedKeyspace();
  }

  @Nullable
  @Override
  public String getNamespace(CassandraRequest request) {
    return request.getSession().getLoggedKeyspace();
  }

  @Deprecated
  @Override
  @Nullable
  public String getConnectionString(CassandraRequest request) {
    return null;
  }

  @Deprecated
  @Override
  @Nullable
  public String getRawStatement(CassandraRequest request) {
    return request.getDbQueryText();
  }

  @Override
  public String getRawQueryText(CassandraRequest request) {
    return request.getDbQueryText();
  }
}
