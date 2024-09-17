/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.cassandra.v4_4;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlClientAttributesGetter;
import javax.annotation.Nullable;

final class CassandraSqlAttributesGetter implements SqlClientAttributesGetter<CassandraRequest> {
  // copied from DbIncubatingAttributes.DbSystemValues
  private static final String CASSANDRA = "cassandra";

  @Deprecated
  @Override
  public String getSystem(CassandraRequest request) {
    return CASSANDRA;
  }

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

  @Deprecated
  @Override
  @Nullable
  public String getName(CassandraRequest request) {
    return request.getSession().getKeyspace().map(CqlIdentifier::toString).orElse(null);
  }

  @Nullable
  @Override
  public String getDbNamespace(CassandraRequest request) {
    return request.getSession().getKeyspace().map(CqlIdentifier::toString).orElse(null);
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
