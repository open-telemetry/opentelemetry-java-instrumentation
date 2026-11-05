/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v4_0;

import static io.opentelemetry.semconv.incubating.CassandraIncubatingAttributes.CASSANDRA_CONSISTENCY_LEVEL;
import static io.opentelemetry.semconv.incubating.CassandraIncubatingAttributes.CASSANDRA_COORDINATOR_DC;
import static io.opentelemetry.semconv.incubating.CassandraIncubatingAttributes.CASSANDRA_COORDINATOR_ID;
import static io.opentelemetry.semconv.incubating.CassandraIncubatingAttributes.CASSANDRA_PAGE_SIZE;
import static io.opentelemetry.semconv.incubating.CassandraIncubatingAttributes.CASSANDRA_QUERY_IDEMPOTENT;
import static io.opentelemetry.semconv.incubating.CassandraIncubatingAttributes.CASSANDRA_SPECULATIVE_EXECUTION_COUNT;

import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverExecutionProfile;
import com.datastax.oss.driver.api.core.cql.ExecutionInfo;
import com.datastax.oss.driver.api.core.cql.Statement;
import com.datastax.oss.driver.api.core.metadata.Node;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import java.util.UUID;
import javax.annotation.Nullable;

final class CassandraAttributesExtractor
    implements AttributesExtractor<CassandraRequest, ExecutionInfo> {

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, CassandraRequest request) {}

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      CassandraRequest request,
      @Nullable ExecutionInfo executionInfo,
      @Nullable Throwable error) {
    if (executionInfo == null) {
      return;
    }

    Node coordinator = executionInfo.getCoordinator();
    if (coordinator != null) {
      String coordinatorDc = coordinator.getDatacenter();
      attributes.put(CASSANDRA_COORDINATOR_DC, coordinatorDc);
      UUID coordinatorId = coordinator.getHostId();
      String coordinatorIdAsString = coordinatorId == null ? null : coordinatorId.toString();
      attributes.put(CASSANDRA_COORDINATOR_ID, coordinatorIdAsString);
    }
    attributes.put(
        CASSANDRA_SPECULATIVE_EXECUTION_COUNT, executionInfo.getSpeculativeExecutionCount());

    Statement<?> statement = executionInfo.getStatement();
    String consistencyLevel;
    DriverExecutionProfile config =
        request.getSession().getContext().getConfig().getDefaultProfile();
    if (statement.getConsistencyLevel() != null) {
      consistencyLevel = statement.getConsistencyLevel().name();
    } else {
      consistencyLevel = config.getString(DefaultDriverOption.REQUEST_CONSISTENCY);
    }
    attributes.put(CASSANDRA_CONSISTENCY_LEVEL, consistencyLevel);

    if (statement.getPageSize() > 0) {
      attributes.put(CASSANDRA_PAGE_SIZE, statement.getPageSize());
    } else {
      int pageSize = config.getInt(DefaultDriverOption.REQUEST_PAGE_SIZE);
      if (pageSize > 0) {
        attributes.put(CASSANDRA_PAGE_SIZE, pageSize);
      }
    }

    Boolean idempotent = statement.isIdempotent();
    if (idempotent == null) {
      idempotent = config.getBoolean(DefaultDriverOption.REQUEST_DEFAULT_IDEMPOTENCE);
    }
    attributes.put(CASSANDRA_QUERY_IDEMPOTENT, idempotent);
  }
}
