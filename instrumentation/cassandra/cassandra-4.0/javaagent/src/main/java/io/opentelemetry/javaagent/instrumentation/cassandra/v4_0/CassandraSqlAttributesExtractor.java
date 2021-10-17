/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v4_0;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.cql.ExecutionInfo;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.api.instrumenter.db.SqlAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;

final class CassandraSqlAttributesExtractor
    extends SqlAttributesExtractor<CassandraRequest, ExecutionInfo> {

  @Override
  protected String system(CassandraRequest request) {
    return SemanticAttributes.DbSystemValues.CASSANDRA;
  }

  @Override
  @Nullable
  protected String user(CassandraRequest request) {
    return null;
  }

  @Override
  @Nullable
  protected String name(CassandraRequest request) {
    return request.getSession().getKeyspace().map(CqlIdentifier::toString).orElse(null);
  }

  @Override
  @Nullable
  protected String connectionString(CassandraRequest request) {
    return null;
  }

  @Override
  protected AttributeKey<String> dbTableAttribute() {
    return SemanticAttributes.DB_CASSANDRA_TABLE;
  }

  @Override
  @Nullable
  protected String rawStatement(CassandraRequest request) {
    return request.getStatement();
  }
}
