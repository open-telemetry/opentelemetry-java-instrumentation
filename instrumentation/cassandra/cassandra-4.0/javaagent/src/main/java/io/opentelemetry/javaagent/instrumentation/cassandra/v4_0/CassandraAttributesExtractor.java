/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v4_0;

import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverExecutionProfile;
import com.datastax.oss.driver.api.core.cql.ExecutionInfo;
import com.datastax.oss.driver.api.core.cql.Statement;
import com.datastax.oss.driver.api.core.metadata.Node;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.checkerframework.checker.nullness.qual.Nullable;

final class CassandraAttributesExtractor
    extends AttributesExtractor<CassandraRequest, ExecutionInfo> {

  @Override
  protected void onStart(AttributesBuilder attributes, CassandraRequest request) {}

  @Override
  protected void onEnd(
      AttributesBuilder attributes, CassandraRequest request, @Nullable ExecutionInfo response) {
    if (response == null) {
      return;
    }

    Node coordinator = response.getCoordinator();
    if (coordinator != null) {
      if (coordinator.getDatacenter() != null) {
        set(
            attributes,
            SemanticAttributes.DB_CASSANDRA_COORDINATOR_DC,
            coordinator.getDatacenter());
      }
      if (coordinator.getHostId() != null) {
        set(
            attributes,
            SemanticAttributes.DB_CASSANDRA_COORDINATOR_ID,
            coordinator.getHostId().toString());
      }
    }
    set(
        attributes,
        SemanticAttributes.DB_CASSANDRA_SPECULATIVE_EXECUTION_COUNT,
        (long) response.getSpeculativeExecutionCount());

    Statement<?> statement = response.getStatement();
    DriverExecutionProfile config =
        request.getSession().getContext().getConfig().getDefaultProfile();
    if (statement.getConsistencyLevel() != null) {
      set(
          attributes,
          SemanticAttributes.DB_CASSANDRA_CONSISTENCY_LEVEL,
          statement.getConsistencyLevel().name());
    } else {
      set(
          attributes,
          SemanticAttributes.DB_CASSANDRA_CONSISTENCY_LEVEL,
          config.getString(DefaultDriverOption.REQUEST_CONSISTENCY));
    }
    if (statement.getPageSize() > 0) {
      set(attributes, SemanticAttributes.DB_CASSANDRA_PAGE_SIZE, (long) statement.getPageSize());
    } else {
      int pageSize = config.getInt(DefaultDriverOption.REQUEST_PAGE_SIZE);
      if (pageSize > 0) {
        set(attributes, SemanticAttributes.DB_CASSANDRA_PAGE_SIZE, (long) pageSize);
      }
    }
    if (statement.isIdempotent() != null) {
      set(attributes, SemanticAttributes.DB_CASSANDRA_IDEMPOTENCE, statement.isIdempotent());
    } else {
      set(
          attributes,
          SemanticAttributes.DB_CASSANDRA_IDEMPOTENCE,
          config.getBoolean(DefaultDriverOption.REQUEST_DEFAULT_IDEMPOTENCE));
    }
  }
}
